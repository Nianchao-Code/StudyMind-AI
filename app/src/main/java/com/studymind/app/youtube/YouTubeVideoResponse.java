package com.studymind.app.youtube;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/** Response from YouTube Data API v3 videos.list */
public class YouTubeVideoResponse {
    public List<Item> items;

    public static class Item {
        public String id;
        public Snippet snippet;
    }

    public static class Snippet {
        public String title;
        public String description;
        @SerializedName("channelTitle") public String channelTitle;
    }
}
