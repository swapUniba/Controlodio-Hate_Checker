$(document).ready(function () {
    $("#tabella_odio").hide();
    $("#esempi").hide();
    $("#searchTwitterUser").click(function () {
        var twitterRequestBody = {};
        twitterRequestBody["screenName"] = $("#twitterScreenName").val();
        $.ajax({
            type: "POST",
            crossDomain: true,
            crossOrigin: true,
            url: "http://localhost:8081/searchTwitterUser",
            contentType: "application/json; charset=utf-8",
            data: JSON.stringify(twitterRequestBody),
            success: function (msg) {
                if(!(msg === '')) {
                    var totale_tweet = msg["total_tweet"];
                    $("p.numero_tweet").html("<b>Risultati analisi di \""+msg["profile"]["name"]+"\" su un totale di " + totale_tweet + " tweet<br>Ripartizione delle categorie di odio su eventuali hate speech rilevate </b>");
                    $("td.Homophobia").html(valutaImpattoCategoria(msg["profile"]["categorie"]["homophobia"]));
                    $("td.Xenophobia").html(valutaImpattoCategoria(msg["profile"]["categorie"]["xenophobia"]));
                    $("td.Disability").html(valutaImpattoCategoria(msg["profile"]["categorie"]["disability"]));
                    $("td.Sexism").html(valutaImpattoCategoria(msg["profile"]["categorie"]["sexism"]));
                    $("td.Anti-semitism").html(valutaImpattoCategoria(msg["profile"]["categorie"]["anti-semitism"]));
                    $("td.Racism").html(valutaImpattoCategoria(msg["profile"]["categorie"]["racism"]));
                    //var hate_tweets_number = parseInt(msg["homophobia"]) + parseInt(msg["xenophobia"]) + parseInt(msg["disability"]) + parseInt(msg["sexism"]) + parseInt(msg["anti_semitism"]) + parseInt(msg["racism"]);
                    var tweet_flag = false;
                    $(".scrollable").empty();
                    var count = 1;
                    msg["tweet"].forEach(function (item) {
                        $("#lista_tweet_offensivi").append("<p class=\"card-text\">"+(count)+") "+item+"</p>");
                        tweet_flag = true;
                        count++;
                    });
                    if(tweet_flag){
                        $("#esempi").show();
                    }else{
                        $("#esempi").hide();
                    }
                    console.log(msg["tweet"]);
                    $("#tabella_odio").show();
                    if(msg["connections"]){
                        if (typeof msg["hate_profile"] != "undefined" && msg["hate_profile"] != null && msg["hate_profile"].length != null && msg["hate_profile"].length > 0) {
                            $("#rete_sociale").empty();
                            $("#rete_sociale").append("<p class=\"card-text\" align='center'><b>Il "+msg["percentage"].toFixed(2)+"% ("+msg["hate_profile"].length+"/"+msg["actual_connections_analyzed"]+") degli utenti presenti nella rete sociale sono possibili haters</b></p align='left'>");
                            /*msg["hate_profile"].forEach(function (item, index) {
                                $("#rete_sociale").append("∎"+item["screenName"] + " ");
                            });*/
                            $("#rete_sociale").append("</p>");
                        }
                    }else{
                        $("#rete_sociale").empty();
                    }
                }else{
                    $("#tabella_odio").hide();
                    $("#esempi").hide();
                    $("p.numero_tweet").html("<font size=\"4\" color=\"red\"><b>Profilo non disponibile</b></font>");
                }
                console.log(msg);
            },
            error: function (xhr, status, errorThrown) {
                alert(status, errorThrown);
                // Error block
                console.log("xhr: " + xhr);
                console.log("status: " + status);
                console.log("errorThrown: " + errorThrown);
            }
        });
    });
});

function valutaImpattoCategoria(occorrenze) {
    occorrenze = parseInt(occorrenze);
    if(occorrenze === 0){
        return "<p align=\"center\">"+String.fromCodePoint(0x1F604)+"</p>";
    }else if(occorrenze >= 1 && occorrenze < 3){
        return "<font color=\"#ff4500\">"+String.fromCodePoint(0x1F61F)+"</font>";
    }else{
        return "<font color=\"red\">"+String.fromCodePoint(0x1F621)+"</font>";
    }
}

