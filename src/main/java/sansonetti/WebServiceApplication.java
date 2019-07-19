package sansonetti;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import sansonetti.controller.WebService;

@SpringBootApplication
public class WebServiceApplication implements CommandLineRunner {
    @Autowired
    WebService webService;
    public static void main(String[] args) {
        SpringApplication.run(WebServiceApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        webService.readConfigFile("config2.txt");
        webService.readCSVFileVocabulary("nuovo_lessico_mappa_con_plurale.csv");

        //Uncomment to enable the statistical analysis of the tool
        //webService.readCSVProfile("200Profili.csv");
    }
}
