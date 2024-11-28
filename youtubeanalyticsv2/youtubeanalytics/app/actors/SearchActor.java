package actors;

import actors.SentimentAnalysisActor;
import actors.WordStatsActor;
import akka.actor.AbstractActorWithTimers;
import akka.actor.Actor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.pattern.Patterns;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import play.cache.AsyncCacheApi;
import play.libs.Json;
import play.libs.ws.WSClient;
import play.libs.ws.WSResponse;
import scala.concurrent.duration.Duration;
import services.ReadabilityCalculator;

import static actors.WordStatsActor.wordStatsMap;

public class SearchActor extends AbstractActorWithTimers {

	private final List<ActorRef> userActorList;
	private final Map<String, ObjectNode> videoNodes;
	private final WSClient ws;
	private final String query;
	private String searchSentiment;
	//	private AsyncCacheApi cache;
	private ActorRef readabilityCalculatorActor;
	private ActorRef sentimentAnalysisActor;
	private ActorRef wordStatsActor;
	private static final String YOUTUBE_API_KEY =
		"AIzaSyBayKpnm9_09dL7QQ6LY84XWPOjBDp_mWs";
	private static final String YOUTUBE_URL =
		"https://www.googleapis.com/youtube/v3";

	public SearchActor(
		WSClient ws,
		String query,
		AsyncCacheApi cache,
		ActorRef readabilityCalculatorActor,
		ActorRef sentimentAnalysisActor,
		ActorRef wordStatsActor
	) {
		this.ws = ws;
		this.query = query;
		//		this.cache = cache;
		this.userActorList = new ArrayList<>();
		this.videoNodes = new HashMap<>();
		this.readabilityCalculatorActor = readabilityCalculatorActor;
		this.sentimentAnalysisActor = sentimentAnalysisActor;
		this.wordStatsActor = wordStatsActor;
		this.searchSentiment = ":-|||||";
	}

	public static Props props(
		WSClient ws,
		String query,
		AsyncCacheApi cache,
		ActorRef readabilityCalculatorActor,
		ActorRef sentimentAnalysisActor,
		ActorRef wordStatsActor
	) {
		return Props.create(
			SearchActor.class,
			ws,
			query,
			cache,
			readabilityCalculatorActor,
			sentimentAnalysisActor,
			wordStatsActor
		);
	}

	//	public static SearchActor getInstance(String query) {
	//		if (!instances.containsKey(query)) {
	//			return null;
	//		}
	//		return instances.get(query);
	//	}

	@Override
	public void preStart() {
		getTimers()
			.startPeriodicTimer(
				"Timer",
				new Tick(this.query),
				Duration.create(10, TimeUnit.MINUTES)
			);
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder()
			.match(Tick.class, message -> {
				if (message.getQuery().equals(this.query)) {
					handleSearch();
				}
			})
			.match(RegisterMsg.class, message -> {
				if (message.getQuery().equals(this.query)) {
					userActorList.add(getSender());
					handleSearch();
				}
			})
			//				.match(SearchResponse.class, message -> {
			//					userActorList.forEach(userActor -> {
			//							userActor.tell(message.response, getSelf());
			//						});
			//				})
			.match(ReadabilityCalculator.ReadabilityResults.class, message -> {
				ObjectNode videoNode = videoNodes.get(message.videoId);
				videoNode.put(
					"fleschKincaidGradeLevel",
					String.format("%.2f", message.gradeLevel)
				);
				videoNode.put(
					"fleschReadingScore",
					String.format("%.2f", message.readingScore)
				);
			})
			.match(
				SentimentAnalysisActor.SentimentAnalysisResults.class,
				message -> {
					this.searchSentiment = message.sentiment;
				}
			)
				.match(WordStatsActor.WordStatsResults.class, message -> {
					JsonNode wordStats = message.wordStats;
					wordStatsMap.put(message.videoId, wordStats);
				})
			.build();
	}

	//	public class Video {
	//
	//	}

	public final class Tick {

		private final String query;

		public Tick(String query) {
			this.query = query;
		}

		public String getQuery() {
			return query;
		}
	}

	public static final class RegisterMsg {

		private final String query;

		public RegisterMsg(String query) {
			this.query = query;
		}

		public String getQuery() {
			return query;
		}
	}

	public static class SearchResponse {

		final String query;
		final ObjectNode response;

		public SearchResponse(String query, ObjectNode response) {
			this.query = query;
			this.response = response;
		}
	}

	// private void handleSearch() {
	// 	if (!userActorList.isEmpty()) {
	// 		ws
	// 			.url(YOUTUBE_URL + "/search")
	// 			.addQueryParameter("part", "snippet")
	// 			.addQueryParameter("maxResults", "1")
	// 			.addQueryParameter("q", query)
	// 			.addQueryParameter("type", "video")
	// 			.addQueryParameter("order", "date")
	// 			.addQueryParameter("key", YOUTUBE_API_KEY)
	// 			.get()
	// 			.thenCompose(youtubeResponse -> {
	// 				JsonNode rawData = youtubeResponse.asJson();
	// 				System.out.println("Line0: API Response");
	// 				//					sender.tell(
	// 				//						new Messages.SearchUpdate(
	// 				//							"completed",
	// 				//							response.asJson()
	// 				//						),
	// 				//						getSelf()
	// 				//					);
	// 				JsonNode items = rawData.get("items");
	// 				// System.out.println("Line1");
	// 				System.out.println(rawData);
	// 				ObjectNode modifiedResponse = rawData.deepCopy();
	// 				ArrayNode modifiedItems =
	// 					JsonNodeFactory.instance.arrayNode();
	// 				List<CompletableFuture<ObjectNode>> futures =
	// 					new ArrayList<>();
	// 				System.out.println("Line2");
	// 				// System.out.println(rawData);
	// 				//
	// 				for (JsonNode item : items) {
	// 					ObjectNode videoNode = (ObjectNode) item;
	// 					String videoId = videoNode
	// 						.get("id")
	// 						.get("videoId")
	// 						.asText();
	// 					videoNodes.put(videoId, videoNode);
	// 					CompletionStage<ObjectNode> future = getVideo(
	// 						videoId
	// 					).thenApply(response -> {
	// 						String description = response
	// 							.asJson()
	// 							.get("items")
	// 							.get(0)
	// 							.get("snippet")
	// 							.get("description")
	// 							.asText();
	// 						System.out.println("Line3");

	// 						readabilityCalculatorActor.tell(
	// 							new ReadabilityCalculator.initReadabilityCalculatorService(
	// 								videoId,
	// 								description
	// 							),
	// 							getSelf()
	// 						);

	// 						videoNode.put("description", description);
	// 						return videoNode;
	// 					});
	// 					futures.add(future.toCompletableFuture());
	// 				}
	// 				//					System.out.println("futures: " + futures);

	// 				System.out.println("Line4");

//	 				CompletableFuture.allOf(
//	 					futures.toArray(new CompletableFuture[0])
//	 				).thenApply(v -> {
//	 					List<Double> grades = new ArrayList<>();
//	 					List<Double> scores = new ArrayList<>();
//	 					List<String> descriptions = new ArrayList<>();
//	 					 CompletableFuture<Void> delay =
//	 					 	CompletableFuture.runAsync(() -> {
//	 					 		try {
//	 					 			Thread.sleep(4000);
//	 					 		} catch (InterruptedException e) {
//	 					 			e.printStackTrace();
//	 					 		}
//	 					 	});
//
//	 					 // This will block until the delay is complete
//	 					 delay.join();
	// 					System.out.println("Line5.1");
	// 					futures
	// 						.stream()
	// 						.map(CompletionStage::toCompletableFuture)
	// 						.map(future -> future.getNow(null))
	// 						.limit(10)
	// 						.forEach(videoNode -> {
	// 							double grade = Double.parseDouble(
	// 								videoNode
	// 									.get("fleschKincaidGradeLevel")
	// 									.asText()
	// 							);
	// 							System.out.println("Line5");
	// 							double score = Double.parseDouble(
	// 								videoNode.get("fleschReadingScore").asText()
	// 							);
	// 							System.out.println("Line5.2");
	// 							grades.add(grade);
	// 							scores.add(score);
	// 							descriptions.add(
	// 								videoNode.get("description").asText()
	// 							);
	// 							modifiedItems.add(videoNode);
	// 							System.out.println("Line5.3");
	// 						});
	// 					// System.out.println("modifiedItems: " + modifiedItems);

	// 					double gradeAvg = grades
	// 						.stream()
	// 						.mapToDouble(Double::doubleValue)
	// 						.average()
	// 						.orElse(0.0);
	// 					double scoreAvg = scores
	// 						.stream()
	// 						.mapToDouble(Double::doubleValue)
	// 						.average()
	// 						.orElse(0.0);

	// 					modifiedResponse.put(
	// 						"fleschKincaidGradeLevelAvg",
	// 						String.format("%.2f", gradeAvg)
	// 					);
	// 					modifiedResponse.put(
	// 						"fleschReadingScoreAvg",
	// 						String.format("%.2f", scoreAvg)
	// 					);
	// 					modifiedResponse.set("items", modifiedItems);
	// 					modifiedResponse.put("query", query);

	// 					sentimentAnalysisActor.tell(
	// 						new SentimentAnalysisActor.initSentimentAnalyzerService(
	// 							query,
	// 							descriptions
	// 						),
	// 						getSelf()
	// 					);
	// 					System.out.println("Line6");

	// 					modifiedResponse.put("sentiment", this.searchSentiment);

	// 					userActorList.forEach(userActor -> {
	// 						userActor.tell(
	// 							new SearchResponse(query, modifiedResponse),
	// 							getSelf()
	// 						);
	// 					});
	// 					System.out.println("Line7");

	// 					return null;
	// 				});
	// 				return null;
	// 			});
	// 	}
	// }
	private void handleSearch() {
		if (!userActorList.isEmpty()) {
			ws
				.url(YOUTUBE_URL + "/search")
				.addQueryParameter("part", "snippet")
				.addQueryParameter("maxResults", "1")
				.addQueryParameter("q", query)
				.addQueryParameter("type", "video")
				.addQueryParameter("order", "date")
				.addQueryParameter("key", YOUTUBE_API_KEY)
				.get()
				.thenCompose(youtubeResponse -> {
					JsonNode rawData = youtubeResponse.asJson();
					System.out.println("Line0: API Response");
					JsonNode items = rawData.get("items");
					List<String> allDescriptions = new ArrayList<>();
					ObjectNode modifiedResponse = rawData.deepCopy();
					ArrayNode modifiedItems =
						JsonNodeFactory.instance.arrayNode();
					List<CompletableFuture<ObjectNode>> futures =
						new ArrayList<>();

					// 1. Get all videos and their descriptions
					for (JsonNode item : items) {
						ObjectNode videoNode = (ObjectNode) item;
						String videoId = videoNode
							.get("id")
							.get("videoId")
							.asText();
						videoNodes.put(videoId, videoNode);

						CompletionStage<ObjectNode> future = getVideo(
							videoId
						).thenCompose(response -> {
							String description = response
								.asJson()
								.get("items")
								.get(0)
								.get("snippet")
								.get("description")
								.asText();
							allDescriptions.add(description);

							// 2. For each video, ask readability actor
							return Patterns.ask(
								readabilityCalculatorActor,
								new ReadabilityCalculator.initReadabilityCalculatorService(
									videoId,
									description
								),
								java.time.Duration.ofSeconds(5)
							).thenApply(readabilityResult -> {
								ReadabilityCalculator.ReadabilityResults results =
									(ReadabilityCalculator.ReadabilityResults) readabilityResult;

								videoNode.put("description", description);
								videoNode.put(
									"fleschKincaidGradeLevel",
									String.format("%.2f", results.gradeLevel)
								);
								videoNode.put(
									"fleschReadingScore",
									String.format("%.2f", results.readingScore)
								);
								return videoNode;
							});
						});
						futures.add(future.toCompletableFuture());
					}

					// 3. After all videos are processed with readability
					return CompletableFuture.allOf(
						futures.toArray(new CompletableFuture[0])
					).thenCompose(v -> {
						List<Double> grades = new ArrayList<>();
						List<Double> scores = new ArrayList<>();
						List<String> descriptions = new ArrayList<>();

						futures
							.stream()
							.map(CompletionStage::toCompletableFuture)
							.map(future -> future.getNow(null))
							.forEach(videoNode -> {
								double grade = Double.parseDouble(
									videoNode
										.get("fleschKincaidGradeLevel")
										.asText()
								);
								double score = Double.parseDouble(
									videoNode.get("fleschReadingScore").asText()
								);
								grades.add(grade);
								scores.add(score);
								descriptions.add(
									videoNode.get("description").asText()
								);
								modifiedItems.add(videoNode);
							});

						double gradeAvg = grades
							.stream()
							.mapToDouble(Double::doubleValue)
							.average()
							.orElse(0.0);
						double scoreAvg = scores
							.stream()
							.mapToDouble(Double::doubleValue)
							.average()
							.orElse(0.0);

						modifiedResponse.put(
							"fleschKincaidGradeLevelAvg",
							String.format("%.2f", gradeAvg)
						);
						modifiedResponse.put(
							"fleschReadingScoreAvg",
							String.format("%.2f", scoreAvg)
						);
						modifiedResponse.set("items", modifiedItems);
						modifiedResponse.put("query", query);

						// Send the descriptions to the WordStatsService
						wordStatsActor.tell(new WordStatsActor.InitWordStatsService(query, allDescriptions), getSelf());

						// 4. Ask sentiment analysis actor
						return Patterns.ask(
							sentimentAnalysisActor,
							new SentimentAnalysisActor.initSentimentAnalyzerService(
								query,
								descriptions
							),
							java.time.Duration.ofSeconds(5)
						).thenApply(sentimentResult -> {
							SentimentAnalysisActor.SentimentAnalysisResults results =
								(SentimentAnalysisActor.SentimentAnalysisResults) sentimentResult;

							modifiedResponse.put(
								"sentiment",
								results.sentiment
							);

							// 5. Finally, send to user actors
							userActorList.forEach(userActor -> {
								userActor.tell(
									new SearchResponse(query, modifiedResponse),
									getSelf()
								);
							});

							return modifiedResponse;
						});
					});
				});
		}
	}

	public CompletionStage<WSResponse> getVideo(String video_id) {
		//		return cache.getOrElseUpdate(
		//				video_id,
		//				() -> {
		return ws
			.url(YOUTUBE_URL + "/videos")
			.addQueryParameter("part", "snippet")
			.addQueryParameter("id", video_id)
			.addQueryParameter("key", YOUTUBE_API_KEY)
			.get();
		//				}
		//				3600
		//		);
	}

	private JsonNode processSearchResults(JsonNode rawData) {
		ArrayNode items = (ArrayNode) rawData.get("items");
		ArrayNode processedItems = Json.newArray();

		for (int i = 0; i < items.size(); i++) {
			JsonNode item = items.get(i);
			ObjectNode processedItem = (ObjectNode) item.deepCopy();
			// Add index to each item
			processedItem.put("index", i + 1);
			processedItems.add(processedItem);
		}

		ObjectNode result = Json.newObject();
		result.set("items", processedItems);
		return result;
	}
}