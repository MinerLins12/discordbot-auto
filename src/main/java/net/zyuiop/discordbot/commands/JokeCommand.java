package net.zyuiop.discordbot.commands;

import net.zyuiop.discordbot.DiscordBot;
import org.apache.commons.lang3.StringEscapeUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import sx.blah.discord.handle.obj.IMessage;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.Assert.assertEquals;

// Created by Saralfddin on 31.10.16.
// Edited by Loris Witschard on 01.11.16.


public class JokeCommand extends DiscordCommand
{
    private Map<Source, String> next = new ConcurrentHashMap<>(); // Fuck this shit
    private Source src = Source.AJ;


    private enum Source { AJ, ETI }

    public JokeCommand() throws Exception
    {
        super("joke", "affiche une blague (alias !hhh)");
        addAlias("hhh");

        new Thread(() -> {
            try {
                next.put(Source.AJ, initURL("http://www.anti-joke.com", 5));
                next.put(Source.ETI, initURL("http://www.explainthisimage.com", 6));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    @Override
    public void run(IMessage message) throws Exception
    {
        String answer = exec(message.getContent().toLowerCase().split("[\\s]+"));
        DiscordBot.sendMessageAutoSplit(message.getChannel(), answer);
    }

    public String exec(String[] args) throws Exception
    {
        if(args.length > 2)
            args = new String[]{"", "err"};

        else if(args.length == 1)
            args = new String[]{"", "help"};

        switch(args[1])
        {
            case "aj":
                src = Source.AJ;
                break;

            case "eti":
                src = Source.ETI;
                break;

            case "dtc":
                return getDTCJoke();
            case "vdm":
                int attempts = 0;
                while (attempts < 10) {
                    try {
                        return getVDMJoke();
                    } catch (Exception e) {
                        attempts++;
                    }
                }
                return "Erreur !";
            case "help":
                return	"*Afficheur de blagues v1.0.1*\n" +
                        "*par Saralfddin & Loris Witschard*\n\n" +
                        "**Utilisation** :\n" +
                        "`!joke aj` : affiche une blague d'*anti-joke.com*\n" +
                        "`!joke eti` : affiche une image d'*explainthisimage.com*\n" +
                        "`!joke dtc` : affiche une blague de *danstonchat.com*\n" +
                        "`!joke vdm` : affiche une blague de *VDM*\n" +
                        "`!joke help` : affiche l'aide";

            default:
                return "*Erreur de syntaxe.*";
        }

        if (!next.containsKey(src)) {
            return "**Cette commande n'est pas encore prête !**";
        }

        Document doc = Jsoup.connect(next.get(src)).get();

        String content = getContent(doc) + "\n\nScore: " + getScore(doc);
        next.replace(src, getNext(doc));

        return content;
    }


    private String getDTCJoke() {
        int id = new Random().nextInt(18085);
        String url = "http://danstonchat.com/" + id + ".html";
        try {
            Document doc = Jsoup.connect(url).get();
            Element item = doc.body().getElementsByClass("item").first();
            System.out.println(item.children().toString());
            Element joke = item.child(0).child(0);
            Element meta = item.child(1).child(0);
            String data = joke.html().replace("<br>", "\n");
            data = data.replaceAll("<span class=\"decoration\">(.+)</span>", "**$1**");
            data = StringEscapeUtils.unescapeHtml4(data);
            return data + "\nVotes : " + meta.child(0).text() + ", " + meta.child(1).text();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "*Erreur !*";
    }

    private String getVDMJoke() {
        String url = "http://www.viedemerde.fr/aleatoire";
        try {
            Document doc = Jsoup.connect(url).userAgent("curl").get();
            Element joke = doc.body().getElementsByClass("article").first();

            String text = joke.child(0).child(0).text();
            Element dateBlock = joke.child(2);
            Element votes = dateBlock.child(0);
            String valid = votes.child(1).child(1).text();
            String tlbm = votes.child(2).child(1).text();
            return text + "\n(Catégorie : " + dateBlock.child(1).child(0).text() + ", Je valide : " + valid + ", TLBM : " + tlbm + ")";
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "*Erreur !*";
    }

    private String initURL(String url, int postNbScale) throws Exception
    {
        boolean success = false;
        String nextUrl = "";
        int postNb = 0;
        int attempt = 1;

        Random rand = new Random();
        int max = (int)Math.pow(10, postNbScale);
        int min = (int)Math.pow(10, postNbScale-1);

        while(!success)
        {
            System.out.print("\rLooking for a valid post in " + url + "... ");
            if(attempt > 1)
                System.out.print("(attempt " + attempt + ") ");

            postNb = rand.nextInt(max - min) + min;
            nextUrl = url + "/posts/" + postNb;
            try
            {
                HttpURLConnection connexion = (HttpURLConnection) new URL(nextUrl).openConnection();
                connexion.connect();
                assertEquals(HttpURLConnection.HTTP_OK, connexion.getResponseCode());
                success = true;
            }
            catch(AssertionError e)
            {
                ++attempt;
            }
        }

        System.out.println("Success! (" + postNb + ")");

        return nextUrl;
    }

    private String getNext(Document doc)
    {
        return doc.select("a:contains(Random)").first().attr("abs:href");
    }

    private String getContent(Document doc)
    {
        switch(src)
        {
            case AJ:
                return "*" + doc.select("h3.content").first().text() + "*";

            case ETI:
                return doc.select("link[rel=image_src]").first().attr("abs:href") + "\n\n"
                        + "Top comment: *" + doc.select("h1.h1").first().text() + "*";

        }
        return "";
    }

    private String getScore(Document doc)
    {
        String score;

        switch(src)
        {
            case AJ:
                score = doc.select("span.value").first().text();
                if(score.length() == 0)
                    return "**0**";
                int value = Integer.parseInt(score);
                return value < 0 ? "**" + value + "** :thumbsdown:" : "**+" + value + "** :thumbsup:";

            case ETI:
                score = doc.select("small.value").first().text();
                if(score.length() == 0)
                    return "**0/5**";
                double average = Double.parseDouble(score.split(" ")[0]);
                return "**" + average + "/5** (" + score.split(" ")[2] + " votes)";
        }
        return "";
    }
}
