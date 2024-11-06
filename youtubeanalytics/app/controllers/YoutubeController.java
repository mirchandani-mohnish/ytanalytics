package controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.inject.Inject;
import models.YoutubeVideo;
import play.libs.ws.*;
import play.mvc.*;
import services.ReadabilityCalculator;
import services.SentimentAnalyzer;
import services.WordStatsService;

public class YoutubeController extends Controller {

	private final WSClient ws;
	private final WordStatsService wordStatsService;
	private static final String YOUTUBE_API_KEY =
		"AIzaSyCRTltXLPxW3IwxMf-WH0BagUipQy_7TuQ";
	private static final String YOUTUBE_URL =
		"https://www.googleapis.com/youtube/v3";

	@Inject
	public YoutubeController(WSClient ws, WordStatsService wordStatsService) {
		this.ws = ws;
		this.wordStatsService = wordStatsService;
	}

	public CompletionStage<ObjectNode> modifyResponse(
		ObjectNode youtubeResponse
	) {
		if (youtubeResponse.has("items")) {
			JsonNode items = youtubeResponse.get("items");
			ObjectNode modifiedResponse = youtubeResponse.deepCopy();
			ArrayNode modifiedItems = JsonNodeFactory.instance.arrayNode();
			List<CompletableFuture<ObjectNode>> futures = new ArrayList<>();
			for (JsonNode item : items) {
				ObjectNode videoNode = (ObjectNode) item;
				String videoId = videoNode.get("id").get("videoId").asText();
				CompletionStage<ObjectNode> future = ws
					.url(YOUTUBE_URL + "/videos")
					.addQueryParameter("part", "snippet")
					.addQueryParameter("id", videoId)
					.addQueryParameter("key", YOUTUBE_API_KEY)
					.get()
					.thenApply(response -> {
						if (response.getStatus() == 200) {
							String description = response
								.asJson()
								.get("items")
								.get(0)
								.get("snippet")
								.get("description")
								.asText();

							Double grade =
								ReadabilityCalculator.calculateFleschKincaidGradeLevel(
									description
								);
							Double score =
								ReadabilityCalculator.calculateFleschReadingScore(
									description
								);
							double sentimentValue =
								SentimentAnalyzer.analyzeDescription(
									description
								);
							videoNode.put("description", description);
							videoNode.put(
								"fleschKincaidGradeLevel",
								String.format("%.2f", grade)
							);
							videoNode.put(
								"fleschReadingScore",
								String.format("%.2f", score)
							);
							return videoNode;
						}
						return videoNode;
					});
				futures.add(future.toCompletableFuture());
			}

			return CompletableFuture.allOf(
				futures
					.stream()
					.map(f -> f.toCompletableFuture())
					.toArray(CompletableFuture[]::new)
			).thenApply(v -> {
				// First, get all results and add to modifiedItems
				futures
					.stream()
					.map(CompletionStage::toCompletableFuture)
					.map(future -> future.getNow(null))
					.forEach(videoNode -> modifiedItems.add(videoNode));

				// Now calculate averages
				double gradeAvg = StreamSupport.stream(
					modifiedItems.spliterator(),
					false
				)
					.mapToDouble(item ->
						Double.parseDouble(
							item.get("fleschKincaidGradeLevel").asText()
						)
					)
					.average()
					.orElse(0.0);

				double scoreAvg = StreamSupport.stream(
					modifiedItems.spliterator(),
					false
				)
					.mapToDouble(item ->
						Double.parseDouble(
							item.get("fleschReadingScore").asText()
						)
					)
					.average()
					.orElse(0.0);

				// Get descriptions for sentiment analysis
				List<String> descriptions = StreamSupport.stream(
					modifiedItems.spliterator(),
					false
				)
					.map(item -> item.get("description").asText())
					.collect(Collectors.toList());

				String sentiment = SentimentAnalyzer.analyzeSentiment(
					descriptions
				);

				// Add the calculated values to the response
				modifiedResponse.put("sentiment", sentiment);
				modifiedResponse.put(
					"fleschKincaidGradeLevelAvg",
					String.format("%.2f", gradeAvg)
				);
				modifiedResponse.put(
					"fleschReadingScoreAvg",
					String.format("%.2f", scoreAvg)
				);

				return modifiedResponse;
			});
		}

		return CompletableFuture.completedFuture(youtubeResponse);
	}

	public CompletionStage<Result> searchVideos(String query) {
		return ws
			.url(YOUTUBE_URL + "/search")
			.addQueryParameter("part", "snippet")
			.addQueryParameter("maxResults", "10")
			.addQueryParameter("q", query)
			.addQueryParameter("type", "video")
			.addQueryParameter("key", YOUTUBE_API_KEY)
			.get()
			.thenCompose(response -> {
				if (response.getStatus() == 200) {
					return modifyResponse(
						(ObjectNode) response.asJson()
					).thenApply(modifiedResponse -> ok(modifiedResponse));
				} else {
					return CompletableFuture.completedFuture(
						internalServerError(
							"YouTube API error: " + response.getBody()
						)
					);
				}
			});
	}

	// New method to get word stats
	public Result getWordStats(String query) {
		// Fetch the list of videos by search query
		List<YoutubeVideo> videos = fetchVideosBySearchTerm(query);

		// Concatenate all descriptions into a single string
		List<String> allDescriptions = videos
			.stream()
			.map(YoutubeVideo::getDescription)
			.collect(Collectors.toList());

		// Split the string into words, normalize to lowercase, and count word frequencies
		Map<String, Long> sortedWordCount = wordStatsService.calculateWordStats(
			allDescriptions
		);

		// Pass the sorted word stats map and search query to the view
		return ok(views.html.wordstats.render(sortedWordCount, query));
	}

	private List<YoutubeVideo> fetchVideosBySearchTerm(String query) {
		// Create an empty list to store the fetched video details
		List<YoutubeVideo> videoList = new ArrayList<>();

		// Perform YouTube search similar to searchVideos method
		CompletionStage<JsonNode> responseStage = ws
			.url(YOUTUBE_URL + "/search")
			.addQueryParameter("part", "snippet")
			.addQueryParameter("maxResults", "50")
			.addQueryParameter("q", query)
			.addQueryParameter("type", "video")
			.addQueryParameter("key", YOUTUBE_API_KEY)
			.get()
			.thenApply(response -> {
				if (response.getStatus() == 200) {
					return response.asJson();
				} else {
					return null; // Handle the error case
				}
			});

		// Wait for the response to complete
		JsonNode response = responseStage.toCompletableFuture().join();

		if (response != null && response.has("items")) {
			JsonNode items = response.get("items");

			// Loop through the search results and fetch details
			for (JsonNode item : items) {
				JsonNode snippet = item.get("snippet");
				String videoId = item.get("id").get("videoId").asText();
				String title = snippet.get("title").asText();
				String description = snippet.get("description").asText();
				String thumbnailUrl = snippet
					.get("thumbnails")
					.get("default")
					.get("url")
					.asText();
				String channelTitle = snippet.get("channelTitle").asText();
				String publishedAt = snippet.get("publishedAt").asText();

				// Fetch additional video stats (like viewCount)
				CompletionStage<JsonNode> videoDetailsStage = ws
					.url(YOUTUBE_URL + "/videos")
					.addQueryParameter("part", "statistics")
					.addQueryParameter("id", videoId)
					.addQueryParameter("key", YOUTUBE_API_KEY)
					.get()
					.thenApply(videoResponse -> {
						if (videoResponse.getStatus() == 200) {
							return videoResponse.asJson();
						} else {
							return null;
						}
					});

				JsonNode videoDetails = videoDetailsStage
					.toCompletableFuture()
					.join();
				Long viewCount = null;
				// Create a YoutubeVideo object and add it to the list
				YoutubeVideo video = new YoutubeVideo(
					videoId,
					title,
					description,
					thumbnailUrl,
					channelTitle,
					publishedAt,
					viewCount
				);
				videoList.add(video);
			}
		}

		return videoList; // Return the list of videos
	}
}
