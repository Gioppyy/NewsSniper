package it.gioppy.rss;

import lombok.Getter;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

public class RssParser {
    @Getter
    private String url;
    private Document doc;
    private News link;


    public RssParser() {
    }

    public RssParser setUrl(String url) {
        this.url = url;
        return this;
    }

    public RssParser build() {
        return this;
    }

    public News getNews() throws IOException {
        LocalDate todayDate = java.time.LocalDate.now(ZoneId.of("GMT"));
        Element i = Jsoup.connect(url).get().select("item").get(0);
        link = new News(
                i.select("title").text(),
                i.select("description").text(),
                i.select("link").text(),
                i.select("pubDate").text()
        );
        DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
        Date dataformat;
        try {
            dataformat = formatter.parse(link.getDate());
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }

        LocalDate pubDate = dataformat.toInstant().atZone(ZoneId.of("GMT")).toLocalDate();

        if (pubDate.isEqual(todayDate)) return link;
        return null;
    }

}