package com.studymind.app.youtube;

import com.studymind.app.BuildConfig;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * YouTube API client. Uses Retrofit for videos.list (metadata).
 */
public class YouTubeClient {
    private static YouTubeApiService service;

    public static YouTubeApiService getService() {
        if (service == null) {
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(HttpLoggingInterceptor.Level.BODY);
            OkHttpClient client = new OkHttpClient.Builder()
                    .addInterceptor(logging)
                    .build();

            Retrofit retrofit = new Retrofit.Builder()
                    .baseUrl(YouTubeApiService.BASE_URL)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
            service = retrofit.create(YouTubeApiService.class);
        }
        return service;
    }

    public static String getApiKey() {
        return BuildConfig.YOUTUBE_API_KEY != null ? BuildConfig.YOUTUBE_API_KEY : "";
    }

    public static boolean hasApiKey() {
        String key = getApiKey();
        return key != null && !key.isEmpty();
    }
}
