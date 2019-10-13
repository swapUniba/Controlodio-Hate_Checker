package sansonetti.controller;

import com.google.common.collect.Lists;
import com.mongodb.MongoWriteException;
import com.opencsv.CSVReader;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import sansonetti.model.*;
import sansonetti.repository.ConnectionCollection;
import sansonetti.repository.MessageCollection;
import sansonetti.repository.ProfileCollection;
import twitter4j.*;
import twitter4j.conf.ConfigurationBuilder;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
public class WebService {
    //Contains hate terms related to the respective categories
    private HashMap<String, List<String>> vocabulary;
    //Used for experiments, counts correct profiles analysis
    private HashMap<String, Integer> correct_category = new HashMap<>();
    //Used for experiments, counts incorrect profiles analysis
    private HashMap<String, Integer> not_correct_category = new HashMap<>();

    private TwitterFactory twitterFactory;
    //Extracts content from Twitter
    private Twitter twitter;
    //Contains the amount of tweet to be extracted
    private static int max_tweet_extraction;

    //Used for read JSON response of a sentiment analysis algorithm
    private String JSON_CHILD_INDEX;
    //Used for read JSON response of a sentiment analysis algorithm
    private String JSON_SCORE_INDEX;
    //Used for read JSON response of a sentiment analysis algorithm
    private String JSON_SCORE_NEG_VALUE;
    //Contains the sentiment analysis algorithm used for processing the content of tweet
    private String SENTIMENT_ANALYSIS_ALGORITHM;

    //Used to retrieve information from Profile collection
    @Autowired
    private ProfileCollection profileCollection;
    //Used to retrieve information from Message collection
    @Autowired
    private MessageCollection messageCollection;
    //Used to retrieve information from Connection collection
    @Autowired
    private ConnectionCollection connectionCollection;

    //Exposes a user's analysis service. Users already analyzed are not extracted again
    @PostMapping("/searchTwitterUser")
    public Result searchTwitterUser(@RequestBody TwitterRequestBody twitterRequestBody) {
        Profile profile = profileCollection.findByScreenNameAndSource(twitterRequestBody.screenName, "twitter");
        if (profile != null) {
            return buildResult(profile);
        } else {
            return searchUser(twitterRequestBody.screenName);
        }
    }
    //Build the response message that is automatically converted into JSON format
    private Result buildResult(Profile profile) {
        Result result = new Result();
        result.setTotal_tweet(messageCollection.countByFromUserAndSource(profile.getScreenName(), "twitter"));
        result.setTweet(getSomeHateTweet(profile.getCategorie(), profile.getScreenName()));
        result.setConnections(profile.isConnections());
        result.setProfile(profile);
        if (profile.isConnections()) {
            List<Connection> connectionList = connectionCollection.findByUsername(profile.getScreenName());
            List<String> screenNameList = new ArrayList<>();
            connectionList.forEach(connection -> screenNameList.add(connection.getContactUsername()));
            List<Profile> contact = new ArrayList<>();
            AtomicInteger total_contact_analyzed = new AtomicInteger();
            total_contact_analyzed.set(0);
            profileCollection.findByScreenNameInAndSource(screenNameList, "twitter").forEach(profile1 -> {
                total_contact_analyzed.incrementAndGet();
                for (String key : profile1.getCategorie().keySet()) {
                    if (profile1.getCategorie().get(key) > 0) {
                        contact.add(profile1);
                        break;
                    }
                }
            });
            contact.forEach(profile1 -> System.out.println(profile1.getScreenName()));
            result.setHate_profile(contact);
            if (result.isConnections()) {
                result.setPercentage(((float) result.getHate_profile().size() * 100) / ((float) (total_contact_analyzed.intValue())));
                result.setActual_connections_analyzed(total_contact_analyzed.intValue());
            }
        }
        return result;
    }
    //Retrieve one hate tweet per category ( if any ), preferably associated with more than one category
    private List<String> getSomeHateTweet(HashMap<String, Integer> categorie, String screenName) {
        List<String> insulti = new ArrayList<>();
        Set<String> already_inserted = new HashSet<>();
        for (String categoria : categorie.keySet()) {
            if (categorie.get(categoria) > 0) {
                List<Message> messages = messageCollection.findByFromUserAndSourceAndCategoryAndScore(screenName, "twitter", categoria, 1);
                messages.sort(new SizeComarator());
                if (messages.get(0).getCategory().size() > 1) {
                    if (!already_inserted.contains(messages.get(0).getText())) {
                        insulti.add(messages.get(0).getText() + " <br><b><font size=\"3\" color=\"red\"> (VALUTAZIONE MOLTO AFFIDABILE)</font></b>");
                        already_inserted.add(messages.get(0).getText());
                    }
                } else {
                    insulti.add(messages.get(0).getText());
                }
            }
        }
        return insulti;
    }
    //Used to order hate tweets based on number of categories they refer to
    class SizeComarator implements Comparator<Message> {
        @Override
        public int compare(Message o1, Message o2) {
            return Integer.valueOf(o2.getCategory().size()).compareTo(o1.getCategory().size());
        }
    }

    private Result searchUser(String username) {
        User user;
        try {
            user = twitter.showUser(username);
            if (user.getStatus() != null) {
                checkApiLimit();
                insertTweet(username, searchUserTweet(username), user);
                return buildResult(profileCollection.findByScreenNameAndSource(username, "twitter"));
            } else {
                System.out.println("@" + user.getScreenName());
                return null;
            }
        } catch (TwitterException error) {
            System.out.println("Profilo inesistente");
            return null;
        }
    }
    //manages API limits by pausing the main thread for the remaining time to request others
    private void handleRateLimit(RateLimitStatus rateLimitStatus) {
        int remaining = rateLimitStatus.getRemaining();
        if (remaining == 0) {
            int resetTime = rateLimitStatus.getSecondsUntilReset() + 5;
            int sleep = (resetTime * 1000);
            try {
                System.out.println("Mi addormento per " + TimeUnit.MINUTES.convert(sleep, TimeUnit.MILLISECONDS) + " minuti");
                Thread.sleep(sleep > 0 ? sleep : 0);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
    //Check API limits for each category and call handleRateLimit
    private void checkApiLimit() {
        Map<String, RateLimitStatus> rateLimitStatus = null;
        try {
            rateLimitStatus = twitter.getRateLimitStatus();
        } catch (TwitterException e) {
            e.printStackTrace();
        }
        for (String endpoint : rateLimitStatus.keySet()) {
            RateLimitStatus status = rateLimitStatus.get(endpoint);
            handleRateLimit(status);
        }
    }
    // Recalls sentiment analysis services for extracted tweets
    private void insertTweet(String screenName, List<Status> statuses, User user) {
        Set<Long> message_already_inserted = new HashSet<>();
        messageCollection.findByFromUserAndSource(screenName, "twitter").iterator().forEachRemaining(e -> message_already_inserted.add(e.getoId()));
        List<Message> message_to_be_insert = new ArrayList<>();
        for (Status status : statuses) {
            Message message_tmp = new Message();
            message_tmp.setFromUser(status.getUser().getScreenName());
            message_tmp.setDate(status.getCreatedAt());
            try {
                message_tmp.setLatitude(status.getGeoLocation().getLatitude());
            } catch (NullPointerException e) {
            }
            try {
                message_tmp.setLongitude(status.getGeoLocation().getLongitude());
            } catch (NullPointerException e) {
            }
            message_tmp.setLanguage(status.getLang());
            message_tmp.setText(status.getText());
            if (status.isRetweet()) {
                message_tmp.setFavs(status.getRetweetedStatus().getFavoriteCount());
            } else {
                message_tmp.setFavs(status.getFavoriteCount());
            }
            message_tmp.setShares(status.getRetweetCount());
            message_tmp.setToUsers(status.getInReplyToScreenName());
            message_tmp.setoId(status.getId());
            message_tmp.setSource("twitter");
            if (!message_already_inserted.contains(status.getId())) {
                message_to_be_insert.add(message_tmp);
            }
        }
        //Divides the array containing the tweets to be submitted to the sentiment analysis service in groups of 50
        List<List<Message>> smallerLists = Lists.partition(message_to_be_insert, 50);
        HashMap<String, Integer> categorie = new HashMap<>();
        categorie.put("homophobia", 0);
        categorie.put("xenophobia", 0);
        categorie.put("disability", 0);
        categorie.put("sexism", 0);
        categorie.put("anti-semitism", 0);
        categorie.put("racism", 0);
        for (List<Message> temp : smallerLists) {
            scoreRequest(temp);
            temp.forEach(message -> {
                message.getCategory().forEach(cat -> {
                    categorie.put(cat, categorie.get(cat) + 1);
                });
            });
            try {
                messageCollection.insert(temp);
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
            }
        }
        insertUser(user, categorie);
    }
    //Apply the sentiment analysis to tweets and at the end label each of these intolerants with the reference hate category
    private void scoreRequest(List<Message> message) {
        String json_string = null;
        try {
            if (SENTIMENT_ANALYSIS_ALGORITHM.equals("sentipolc")) {
                HttpClient client = new DefaultHttpClient();
                HttpResponse response;
                JSONObject post_data = new JSONObject();
                HttpPost post = new HttpPost("http://193.204.187.210:9009/sentipolc/v1/classify");
                JSONArray texts_data = new JSONArray();
                for (Message message_temp : message) {
                    JSONObject text = new JSONObject();
                    text.put("id", String.valueOf(message_temp.getoId()));
                    text.put("text", message_temp.getText());
                    texts_data.put(text);
                }
                post_data.put("texts", texts_data);
                StringEntity se = new StringEntity(post_data.toString());
                post.setEntity(se);
                response = client.execute(post);
                System.out.println("Response Code : " +
                        response.getStatusLine().getStatusCode());
                json_string = EntityUtils.toString(response.getEntity());
            } else if (SENTIMENT_ANALYSIS_ALGORITHM.equals("HanSEL")) {
                JSONArray payload = new JSONArray();
                for (Message message_temp : message) {
                    String tmp = message_temp.getText();
                    tmp = tmp.replaceAll("[^a-zA-Z0-9]", " ");
                    payload.put(tmp);
                }
                System.out.println(payload.toString());
                try {
                    json_string = sendPostRequest("http://90.147.170.25:5000/api/hatespeech", "{\"tweets\":" + payload.toString() + " }");
                }catch (RuntimeException e){
                    System.out.println("IL SERVIZIO DI SENTIMENT HanSEL NON E' RAGGIUNGIBILE, SI STA UTILIZZANDO SENTIPOLC");
                    SENTIMENT_ANALYSIS_ALGORITHM = "sentipolc";
                    this.JSON_CHILD_INDEX = "results";
                    this.JSON_SCORE_INDEX = "polarity";
                    this.JSON_SCORE_NEG_VALUE = "neg";
                    scoreRequest(message);
                    return;
                }
                } else {
                System.out.println("Algoritmo di sentiment configurato non riconosciuto");
                System.exit(0);
            }
            JSONObject temp = new JSONObject(json_string);
            for (int i = 0; i < temp.getJSONArray(JSON_CHILD_INDEX).length(); i++) {
                if (temp.getJSONArray(JSON_CHILD_INDEX).getJSONObject(i).get(JSON_SCORE_INDEX).equals(JSON_SCORE_NEG_VALUE)) {
                    message.get(i).setScore(1);
                    //Ciclo sugli insulti
                    for (String insult : vocabulary.keySet()) {
                        //Ciclo su categoria
                        for (String category : vocabulary.get(insult)) {
                            String[] tweet_arr = message.get(i).getText().toLowerCase().split(" ");
                            String[] insult_arr = insult.split(" ");
                            if (insult_arr.length != 1) {
                                if (message.get(i).getText().replaceAll("[^a-zA-Z0-9]", " ").toLowerCase().contains(insult)) {
                                    if (insult.trim().equalsIgnoreCase("troia") || insult.trim().equalsIgnoreCase("puttana")) {
                                        if (cleanTweet(message.get(i).getText())) {
                                            message.get(i).getCategory().add(category);
                                            System.err.println("Termine sensibile trovato: " + insult);
                                            System.err.println(message.get(i).getText() + " ASSOCIATO ALLA CATEGORIA " + category);
                                        }
                                    } else {
                                        message.get(i).getCategory().add(category);
                                        System.err.println("Termine sensibile trovato: " + insult);
                                        System.err.println(message.get(i).getText() + " ASSOCIATO ALLA CATEGORIA " + category);
                                    }
                                }
                            } else {
                                for (String s : tweet_arr) {
                                    if ((s.replaceAll("[^a-zA-Z0-9]", " ").trim()).equals(insult)) {
                                        if (insult.trim().equalsIgnoreCase("troia") || insult.trim().equalsIgnoreCase("puttana")) {
                                            if (cleanTweet(message.get(i).getText())) {
                                                message.get(i).getCategory().add(category);
                                                System.err.println("Termine sensibile trovato: " + insult);
                                                System.err.println(message.get(i).getText() + " ASSOCIATO ALLA CATEGORIA " + category);
                                            }
                                        } else {
                                            message.get(i).getCategory().add(category);
                                            System.err.println("Termine sensibile trovato: " + insult);
                                            System.err.println(message.get(i).getText() + " ASSOCIATO ALLA CATEGORIA " + category);
                                        }
                                        break;
                                    }
                                }
                            }
                        }
                    }
                } else {
                    message.get(i).setScore(0);
                }
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
    }
    //Used to send tweet to HanSEL sentiment analysis service
    private String sendPostRequest(String requestUrl, String payload) {
        try {
            URL url = new URL(requestUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoInput(true);
            connection.setDoOutput(true);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream(), "UTF-8");
            writer.write(payload);
            writer.close();
            System.out.println("Response Code : " + connection.getResponseCode());
            BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuffer jsonString = new StringBuffer();
            String line;
            while ((line = br.readLine()) != null) {
                jsonString.append(line);
            }
            br.close();
            connection.disconnect();
            return jsonString.toString();
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }

    }
    //Report false intolerant tweets
    private boolean cleanTweet(String tweet) {
        return !tweet.toLowerCase().contains("porca puttana") && !tweet.toLowerCase().contains("porca troia") && !tweet.toLowerCase().contains("figlio di puttana") && !tweet.toLowerCase().contains("figlio di troia") &&
                !tweet.toLowerCase().contains("figli di puttana") && !tweet.toLowerCase().contains("figli di troia") && !tweet.toLowerCase().contains("cavallo di troia");
    }
    //Memorize an analyzed user with an overview of the intolerant tweets identified by category
    private void insertUser(User user, HashMap<String, Integer> categorie) {
        Profile profile = new Profile();
        profile.setScreenName(user.getScreenName());
        try {
            profile.setName(user.getName());
            profile.setDescription(user.getDescription());
            profile.setLocation(user.getLocation());
            profile.setWebsite(user.getURL());
            profile.setCreated_at(user.getCreatedAt());
            profile.setFollowing_count(user.getFriendsCount());
            profile.setFollower_count(user.getFollowersCount());
            profile.setSource("twitter");
            profile.setCategorie(categorie);
            profile.setConnections(false);
            profile.setAggiornatoIl(LocalDate.now());
            profileCollection.deleteByScreenNameAndSource(user.getScreenName(), "twitter");
            profileCollection.insert(profile);
        } catch (MongoWriteException e) {
            System.out.println(e.getMessage());
        }
    }
    //Extracts tweets from the platform
    private List<Status> searchUserTweet(String username) {
        Twitter twitter = twitterFactory.getInstance();
        int page_number = 1;
        List<Status> statuses = new ArrayList<>();
        while (true) {
            try {
                int size = statuses.size();
                Paging page = new Paging(page_number++, 100);
                ResponseList<Status> responseList = twitter.getUserTimeline(username, page);
                if ((statuses.size() + responseList.size()) < max_tweet_extraction) {
                    statuses.addAll(responseList);
                } else {
                    int temp = max_tweet_extraction - statuses.size();
                    statuses.addAll(responseList.subList(0, temp));
                }
                if (statuses.size() == size)
                    break;
            } catch (TwitterException e) {
                e.printStackTrace();
            }
        }
        System.out.println("SONO STATI ESTRATTI " + statuses.size() + " tweet di " + username);
        return statuses;
    }
    //Reads a CSV file containing intolerant terms
    public void readCSVFileVocabulary(String path) {
        vocabulary = new HashMap<>();
        try (CSVReader csvReader = new CSVReader(new FileReader(path))) {
            String[] values;
            while ((values = csvReader.readNext()) != null) {
                for (int i = 0; i < values.length; i++) {
                    if (!values[i].equals("")) {
                        String category = null;
                        switch (i) {
                            case 0:
                                category = "homophobia";
                                break;
                            case 1:
                                category = "racism";
                                break;
                            case 2:
                                category = "disability";
                                break;
                            case 3:
                                category = "sexism";
                                break;
                            case 4:
                                category = "anti-semitism";
                                break;
                            case 5:
                                category = "xenophobia";
                                break;
                        }
                        if (category != null) {
                            if (vocabulary.containsKey(values[i])) {
                                vocabulary.get(values[i]).add(category);
                            } else {
                                List<String> list = new ArrayList<>();
                                list.add(category);
                                vocabulary.put(values[i], list);
                            }
                        }
                    }
                }
            }
            for (String string : vocabulary.keySet()) {
                System.out.println("Per il termine " + string + " sono state trovare le seguenti categorie");
                for (String list : vocabulary.get(string)) {
                    System.out.println(list);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    //Initialize the summary of the hate speeches detected by category
    private void initialize_category_counter(HashMap<String, Integer> tmp) {
        tmp.put("homophobia", 0);
        tmp.put("racism", 0);
        tmp.put("anti-semitism", 0);
        tmp.put("xenophobia", 0);
        tmp.put("sexism", 0);
        tmp.put("disability", 0);
    }
    //Used for statistical purposes, it is used to initialize the dataset on which to perform the experiments
    public void readCSVProfile(String path) {
        initialize_category_counter(correct_category);
        initialize_category_counter(not_correct_category);
        HashMap<String, LinkedList<Integer>> profile = new HashMap<>();
        try (CSVReader csvReader = new CSVReader(new FileReader(path))) {
            String[] values;
            String screenName = null;
            LinkedList<Integer> category;
            while ((values = csvReader.readNext()) != null) {
                category = new LinkedList<>();
                for (int i = 0; i < values.length; i++) {
                    if (!values[i].equals("")) {
                        if (i == 0) {
                            screenName = values[i];
                        } else {
                            category.add(Integer.valueOf(values[i]));
                        }
                    }
                }
                profile.put(screenName, category);
            }
            for (String string : profile.keySet()) {
                System.out.print("Per il profilo " + string + " le categorie sono così ripartite: ");
                for (int tmp : profile.get(string)) {
                    System.out.print(tmp);
                }
                System.out.println();
            }
            System.out.println("Numero profili: " + profile.keySet().size());
            FileWriter csvWriter = new FileWriter("tabulazioni_ibrido_algoritmo_secondario_con_plurale.csv");
            for (String string : profile.keySet()) {
                System.out.println("Profilo : " + string);

                Profile result = profileCollection.findByScreenNameAndSource(string, "twitter");
                if (result == null) {
                    searchUser(string);
                    result = profileCollection.findByScreenNameAndSource(string, "twitter");
                }
                csvWriter.append(string);
                csvWriter.append(",");
                csvWriter.append(String.valueOf(checkAnalysis("homophobia", result.getCategorie().get("homophobia"), profile.get(string).get(0))));
                csvWriter.append(",");
                csvWriter.append(String.valueOf(checkAnalysis("racism", result.getCategorie().get("racism"), profile.get(string).get(1))));
                csvWriter.append(",");
                csvWriter.append(String.valueOf(checkAnalysis("anti-semitism", result.getCategorie().get("anti-semitism"), profile.get(string).get(2))));
                csvWriter.append(",");
                csvWriter.append(String.valueOf(checkAnalysis("xenophobia", result.getCategorie().get("xenophobia"), profile.get(string).get(3))));
                csvWriter.append(",");
                csvWriter.append(String.valueOf(checkAnalysis("sexism", result.getCategorie().get("sexism"), profile.get(string).get(4))));
                csvWriter.append(",");
                csvWriter.append(String.valueOf(checkAnalysis("disability", result.getCategorie().get("disability"), profile.get(string).get(5))));
                csvWriter.append(",");
                csvWriter.append(String.valueOf(profile.get(string).get(0)));
                csvWriter.append(",");
                csvWriter.append(String.valueOf(profile.get(string).get(1)));
                csvWriter.append(",");
                csvWriter.append(String.valueOf(profile.get(string).get(2)));
                csvWriter.append(",");
                csvWriter.append(String.valueOf(profile.get(string).get(3)));
                csvWriter.append(",");
                csvWriter.append(String.valueOf(profile.get(string).get(4)));
                csvWriter.append(",");
                csvWriter.append(String.valueOf(profile.get(string).get(5)));
                csvWriter.append("\n");
                csvWriter.flush();
            }
            csvWriter.close();
            for (String string : correct_category.keySet()) {
                System.out.println("Per la categoria " + string + " le valutazioni corrette sono : " + correct_category.get(string) + "(" + ((correct_category.get(string) * 100) / (correct_category.get(string) + not_correct_category.get(string))) + "%) , quelle non corrette sono " + not_correct_category.get(string));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    //Compare the manual labeling of a category with that performed by Hate Checker
    private int checkAnalysis(String category, int category_occurrences, int flag) {
        if ((category_occurrences > 0 && flag == 1) || (category_occurrences == 0 && flag == 0)) {
            correct_category.put(category, correct_category.get(category) + 1);
        } else {
            not_correct_category.put(category, not_correct_category.get(category) + 1);
        }
        if(category_occurrences>0){
            return 1;
        }else{
            return 0;
        }
    }
    //Reads the configuration file in which the configuration of the workflow to be executed is specified
    public void readConfigFile(String path) {
        HashMap<String, String> configField = new HashMap<>();
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] values = line.split(":");
                configField.put(values[0], values[1]);
                System.out.println(values[0] + " " + values[1]);
            }
            ConfigurationBuilder configurationBuilder = new ConfigurationBuilder().setDebugEnabled(true).setOAuthConsumerKey(configField.get("OAuthConsumerKey")).
                    setOAuthConsumerSecret(configField.get("OAuthConsumerSecret")).
                    setOAuthAccessToken(configField.get("OAuthAccessToken")).
                    setOAuthAccessTokenSecret(configField.get("OAuthAccessTokenSecret")).setTweetModeExtended(true);
            twitterFactory = new TwitterFactory(configurationBuilder.build());
            twitter = twitterFactory.getInstance();
            WebService.max_tweet_extraction = Integer.valueOf(configField.get("max_tweet_extraction"));
            if (configField.get("sentiment_analysis_algorithm").equals("sentipolc")) {
                this.SENTIMENT_ANALYSIS_ALGORITHM = "sentipolc";
                this.JSON_CHILD_INDEX = "results";
                this.JSON_SCORE_INDEX = "polarity";
                this.JSON_SCORE_NEG_VALUE = "neg";

            } else if (configField.get("sentiment_analysis_algorithm").equals("HanSEL")) {
                this.SENTIMENT_ANALYSIS_ALGORITHM = "HanSEL";
                this.JSON_CHILD_INDEX = "results";
                this.JSON_SCORE_INDEX = "class";
                this.JSON_SCORE_NEG_VALUE = "1";
            } else {
                System.out.println("Algoritmo di sentiment configurato non riconosciuto");
                System.exit(0);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
