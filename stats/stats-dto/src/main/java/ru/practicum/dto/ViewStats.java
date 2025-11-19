package main.java.ru.practicum.dto;

import java.util.Objects;

public class ViewStats {
    private String app;
    private String uri;
    private Long hits;

    public String getApp() {
        return app;
    }

    public void setApp(String app) {
        this.app = app;
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public Long getHits() {
        return hits;
    }

    public void setHits(Long hits) {
        this.hits = hits;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ViewStats viewStats = (ViewStats) o;
        return Objects.equals(app, viewStats.app) && Objects.equals(uri, viewStats.uri) && Objects.equals(hits, viewStats.hits);
    }

    @Override
    public int hashCode() {
        return Objects.hash(app, uri, hits);
    }
}
