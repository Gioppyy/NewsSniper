package it.gioppy.rss;

import lombok.Getter;

@Getter
public class News {
    private final String title;
    private final String description;
    private final String url;
    private final String date;

    public News(String title, String description, String url, String date) {
        this.title = title;
        this.description = description;
        this.url = url;
        this.date = date;
    }

}
