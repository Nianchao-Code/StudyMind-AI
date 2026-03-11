package com.studymind.app.youtube;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

/**
 * YouTube Data API v3 - videos.list endpoint.
 * Fetches video metadata (title, description, channel).
 */
public interface YouTubeApiService {
    String BASE_URL = "https://www.googleapis.com/youtube/v3/";

    @GET("videos")
    Call<YouTubeVideoResponse> getVideo(
            @Query("part") String part,
            @Query("id") String videoId,
            @Query("key") String apiKey
    );
}
