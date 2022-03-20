import static com.mongodb.client.model.Filters.*;

import com.mongodb.rx.client.MongoClient;
import com.mongodb.rx.client.MongoClients;
import com.mongodb.rx.client.MongoCollection;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.reactivex.netty.protocol.http.server.HttpServer;
import org.bson.Document;
import org.bson.types.ObjectId;
import rx.Observable;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public class Main {
    private static MongoClient createMongoClient() {
        return MongoClients.create("mongodb://localhost:27017");
    }

    private static MongoCollection<Document> userCollection() {
        return createMongoClient().getDatabase("rxtest").getCollection("user");
    }

    private static MongoCollection<Document> productCollection() {
        return createMongoClient().getDatabase("rxtest").getCollection("product");
    }

    public static void main(final String[] args) throws IOException {
        MongoClient client = createMongoClient();

        String main = Files.readString(Paths.get("main.html"));

        HttpServer
                .newServer(8080)
                .start((req, resp) -> {
                    switch (req.getDecodedPath()) {
                        case "/main.html": {
                            return resp.writeString(Observable.just(main));
                        }
                        case "/add-user": {
                            String name = req.getQueryParameters().get("name").get(0);
                            String currency = req.getQueryParameters().get("currency").get(0);
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
                            MongoCollection<Document> collection = productCollection();

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
                            MongoCollection<Document> products = userCollection();
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
                            Function<String, Observable<JsonObject>> productsToJsonWithCurrency =
                                currency -> {
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
                                };

                            Observable<String> o =
                                    collection
                                            .find(eq("_id", new ObjectId(id)))
                                            .first()
                                            .timeout(3, TimeUnit.SECONDS)
                                            .flatMap(doc -> {
                                                String currency = doc.getString("currency");
                                                return productsToJsonWithCurrency.apply(currency);
                                            })
                                            .toList()
                                            .map(rows -> {
                                                JsonArray arr = new JsonArray();
                                                for (var val : rows) {
                                                    arr.add(val);
                                                }
                                                return arr.toString();
                                            });
                            return resp.writeString(o);
                        }
                        default:
                            resp.setStatus(HttpResponseStatus.BAD_REQUEST);
                            return resp;
                    }
                })
                .awaitShutdown();
    }
}
