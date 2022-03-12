import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Projections.*;

import com.mongodb.rx.client.MongoClient;
import com.mongodb.rx.client.MongoClients;
import com.mongodb.rx.client.MongoCollection;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.reactivex.netty.protocol.http.server.HttpServer;
import org.bson.Document;
import org.bson.types.ObjectId;
import rx.Observable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class Main {
//    private static Observable<User> getAllUsers(MongoCollection<Document> collection) {
//        return collection.find().toObservable().map(User::new);
//    }

    private static MongoClient createMongoClient() {
        return MongoClients.create("mongodb://localhost:27017");
    }

    static class IdGenerator {
        private int id = 0;

        int next() {
            return id++;
        }
    }

    public static void main(final String[] args) throws IOException {
        MongoClient client = createMongoClient();
//        Observable<User> user = getAllUsers(collection);
//        user.subscribe(ReactiveMongoDriverExample::getPrintln);
        IdGenerator userId = new IdGenerator(); //client.getDatabase("rxtest").getCollection("user").aggregate(List.of(Accumulators.max()));
        IdGenerator productId = new IdGenerator();

        String main = Files.readString(Paths.get("main.html"));

        HttpServer
                .newServer(8080)
                .start((req, resp) -> {
                    System.out.println(req.getDecodedPath());
                    System.out.println(req.getQueryParameters());

                    switch (req.getDecodedPath()) {
                        case "/main.html": {
                            System.out.println(main);
                            return resp.writeString(Observable.just(main));
                        }
                        case "/add-user": {
                            System.out.println("adding user...");
                            String name = req.getQueryParameters().get("name").get(0);
                            String currency = req.getQueryParameters().get("currency").get(0);
                            System.out.println(name);
                            System.out.println(currency);
                            final int user = userId.next();
                            MongoCollection<Document> collection = client.getDatabase("rxtest").getCollection("user");
                            Document doc = new Document("name", name)
                                    .append("currency", currency);
                            return resp.writeString(
                                    collection
                                            .insertOne(doc)
                                            .doOnError(System.out::println)
                                            .timeout(3, TimeUnit.SECONDS)
                                            .map(val -> doc.getObjectId("_id").toString()));
                        }
                        case "/add-product": {
                            MongoCollection<Document> collection = client.getDatabase("rxtest").getCollection("product");
                            String name = req.getQueryParameters().get("name").get(0);
                            int euro = Integer.parseInt(req.getQueryParameters().get("e").get(0));
                            int roubles = Integer.parseInt(req.getQueryParameters().get("r").get(0));
                            int dollars = Integer.parseInt(req.getQueryParameters().get("d").get(0));
                            Document doc =
                                    new Document("name", name)
                                            .append("e", euro)
                                            .append("d", dollars)
                                            .append("r", roubles);
                            return resp.writeString(
                                    collection
                                            .insertOne(doc)
                                            .doOnError(System.out::println)
                                            .timeout(3, TimeUnit.SECONDS)
                                            .map(v -> doc.getObjectId("_id").toString()));
                        }
                        case "/get-users": {
                            MongoCollection<Document> products = client.getDatabase("rxtest").getCollection("user");
                            Observable<String> o =
                                    products
                                    .find()
                                    .toObservable()
                                    .timeout(3, TimeUnit.SECONDS)
                                    .map(doc -> {
                                        JsonObject obj = new JsonObject();
                                        obj.addProperty("id", doc.getObjectId("_id").toString());
                                        obj.addProperty("currency", doc.getString("currency"));
                                        obj.addProperty("name", doc.getString("name"));
                                        return obj;
                                    })
                                    .toList()
                                    .map(rows -> {
                                        JsonArray arr = new JsonArray();
                                        for (var v : rows) {
                                            arr.add(v);
                                        }
                                        return arr.toString();
                                    });
                            return resp.writeString(o);
                        }
                        case "/view": {
                            String id = req.getQueryParameters().get("id").get(0);
                            MongoCollection<Document> collection = client.getDatabase("rxtest").getCollection("user");
                            Observable<String> o =
                                    collection
                                            .find(eq("_id", new ObjectId(id)))
                                            .first()
                                            .timeout(3, TimeUnit.SECONDS)
                                            .flatMap(v -> {
                                                String currency = v.getString("currency");
                                                MongoCollection<Document> products = client.getDatabase("rxtest").getCollection("product");
                                                return products
                                                        .find()
                                                        .toObservable()
                                                        .timeout(3, TimeUnit.SECONDS)
                                                        .map(doc -> {
                                                            JsonObject obj = new JsonObject();
                                                            obj.addProperty("id", doc.getObjectId("_id").toString());
                                                            obj.addProperty("value", doc.getInteger(currency));
                                                            obj.addProperty("name", doc.getString("name"));
                                                            return obj;
                                                        });
                                            })
                                            .toList()
                                            .map(rows -> {
                                                JsonArray arr = new JsonArray();
                                                for (var val : rows) {
                                                    arr.add(val);
                                                }
                                                return arr.toString();
                                            });

                            resp.addHeader("Access-Control-Allow-Origin", List.of("origin"));
                            return resp.writeString(o);
                        }
                        default:
                            System.out.println("bad request");
                            resp.setStatus(HttpResponseStatus.BAD_REQUEST);
                            return resp;
                    }

//                    String name = req.getDecodedPath().substring(1);

//                    Observable<String> response = Observable
//                            .just(name)
//                            .map(usd -> "Hello, " + name + "!");
//
//                    return resp.writeString(response);
                })
                .awaitShutdown();
    }
}
