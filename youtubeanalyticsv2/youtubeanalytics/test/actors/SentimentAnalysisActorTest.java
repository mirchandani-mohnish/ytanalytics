package actors;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.testkit.javadsl.TestKit;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import services.SentimentAnalyzer;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

public class SentimentAnalysisActorTest {
    private static ActorSystem system;

    @BeforeClass
    public static void setup() {
        system = ActorSystem.create("TestSystem");
    }

    @AfterClass
    public static void teardown() {
        TestKit.shutdownActorSystem(system);
        system = null;
    }

    @Test
    public void testPositiveSentimentAnalysis() {
        new TestKit(system) {{
            // Prepare test data
            String testQuery = "Happy Videos";
            List<String> positiveDescriptions = Arrays.asList(
                "This is a happy and amazing day!",
                "I love this wonderful experience"
            );

            // Create the actor
            ActorRef sentimentActor = system.actorOf(SentimentAnalysisActor.props());

            // Create a test probe to receive messages
            TestKit probe = new TestKit(system);

            // Prepare the init message
            SentimentAnalysisActor.initSentimentAnalyzerService initMessage = 
                new SentimentAnalysisActor.initSentimentAnalyzerService(testQuery, positiveDescriptions);

            // Send message to the actor
            sentimentActor.tell(initMessage, probe.getRef());

            // Expect a SentimentAnalysisResults message
            SentimentAnalysisActor.SentimentAnalysisResults response = 
                probe.expectMsgClass(
                    java.time.Duration.ofSeconds(5),
                    SentimentAnalysisActor.SentimentAnalysisResults.class
                );

            // Verify the response
            assertEquals(testQuery, response.searchQuery);
            assertEquals(":-)", response.sentiment);
        }};
    }

    @Test
    public void testNegativeSentimentAnalysis() {
        new TestKit(system) {{
            // Prepare test data
            String testQuery = "Sad Videos";
            List<String> negativeDescriptions = Arrays.asList(
                "This is a terrible and awful experience",
                "I hate everything about this situation"
            );

            // Create the actor
            ActorRef sentimentActor = system.actorOf(SentimentAnalysisActor.props());

            // Create a test probe to receive messages
            TestKit probe = new TestKit(system);

            // Prepare the init message
            SentimentAnalysisActor.initSentimentAnalyzerService initMessage = 
                new SentimentAnalysisActor.initSentimentAnalyzerService(testQuery, negativeDescriptions);

            // Send message to the actor
            sentimentActor.tell(initMessage, probe.getRef());

            // Expect a SentimentAnalysisResults message
            SentimentAnalysisActor.SentimentAnalysisResults response = 
                probe.expectMsgClass(
                    java.time.Duration.ofSeconds(5),
                    SentimentAnalysisActor.SentimentAnalysisResults.class
                );

            // Verify the response
            assertEquals(testQuery, response.searchQuery);
            assertEquals(":-(", response.sentiment);
        }};
    }

    @Test
    public void testNeutralSentimentAnalysis() {
        new TestKit(system) {{
            // Prepare test data
            String testQuery = "Neutral Videos";
            List<String> neutralDescriptions = Arrays.asList(
                "This is an okay description",
                "Nothing special happening"
            );

            // Create the actor
            ActorRef sentimentActor = system.actorOf(SentimentAnalysisActor.props());

            // Create a test probe to receive messages
            TestKit probe = new TestKit(system);

            // Prepare the init message
            SentimentAnalysisActor.initSentimentAnalyzerService initMessage = 
                new SentimentAnalysisActor.initSentimentAnalyzerService(testQuery, neutralDescriptions);

            // Send message to the actor
            sentimentActor.tell(initMessage, probe.getRef());

            // Expect a SentimentAnalysisResults message
            SentimentAnalysisActor.SentimentAnalysisResults response = 
                probe.expectMsgClass(
                    java.time.Duration.ofSeconds(5),
                    SentimentAnalysisActor.SentimentAnalysisResults.class
                );

            // Verify the response
            assertEquals(testQuery, response.searchQuery);
            assertEquals(":-|", response.sentiment);
        }};
    }

    @Test
    public void testEmptyDescriptionsSentimentAnalysis() {
        new TestKit(system) {{
            // Prepare test data
            String testQuery = "Empty Videos";
            List<String> emptyDescriptions = Collections.emptyList();

            // Create the actor
            ActorRef sentimentActor = system.actorOf(SentimentAnalysisActor.props());

            // Create a test probe to receive messages
            TestKit probe = new TestKit(system);

            // Prepare the init message
            SentimentAnalysisActor.initSentimentAnalyzerService initMessage = 
                new SentimentAnalysisActor.initSentimentAnalyzerService(testQuery, emptyDescriptions);

            // Send message to the actor
            sentimentActor.tell(initMessage, probe.getRef());

            // Expect a SentimentAnalysisResults message
            SentimentAnalysisActor.SentimentAnalysisResults response = 
                probe.expectMsgClass(
                    java.time.Duration.ofSeconds(5),
                    SentimentAnalysisActor.SentimentAnalysisResults.class
                );

            // Verify the response
            assertEquals(testQuery, response.searchQuery);
            assertEquals(":-|", response.sentiment);
        }};
    }

    @Test
    public void testMixedSentimentAnalysis() {
        new TestKit(system) {{
            // Prepare test data
            String testQuery = "Mixed Sentiment Videos";
            List<String> mixedDescriptions = Arrays.asList(
                "This is a happy day",
                "But something bad happened"
            );

            // Create the actor
            ActorRef sentimentActor = system.actorOf(SentimentAnalysisActor.props());

            // Create a test probe to receive messages
            TestKit probe = new TestKit(system);

            // Prepare the init message
            SentimentAnalysisActor.initSentimentAnalyzerService initMessage = 
                new SentimentAnalysisActor.initSentimentAnalyzerService(testQuery, mixedDescriptions);

            // Send message to the actor
            sentimentActor.tell(initMessage, probe.getRef());

            // Expect a SentimentAnalysisResults message
            SentimentAnalysisActor.SentimentAnalysisResults response = 
                probe.expectMsgClass(
                    java.time.Duration.ofSeconds(5),
                    SentimentAnalysisActor.SentimentAnalysisResults.class
                );

            // Verify the response
            assertEquals(testQuery, response.searchQuery);
            assertEquals(":-|", response.sentiment);
        }};
    }
}