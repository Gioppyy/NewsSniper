package it.gioppy.rss;

import lombok.Getter;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import java.io.IOException;

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
        Element i = Jsoup.connect(url).get().select("item").get(0);
        link = new News(
                i.select("title").text(),
                i.select("description").text(),
                i.select("link").text(),
                i.select("pubDate").text()
            );
        return link;
    }

}