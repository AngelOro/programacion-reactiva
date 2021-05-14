package com.example.demo;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;


import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

@SpringBootApplication
@RestController
public class ReactiveApplication {
    @Value("${app.url:http://example.com}")
    private String url = "http://example.com";

    private static Logger log = LoggerFactory.getLogger(ReactiveApplication.class);
    private RestTemplate restTemplate = new RestTemplate();
    private WebClient client = new WebClient(new ReactorClientHttpConnector());
    private Scheduler scheduler = Schedulers.elastic();

    @RequestMapping("/parallel")
    public Mono<Result> parallel() {
        log.info("Handling /parallel");
        return Flux.range(1, 10) // <1>
                .log() //
                .flatMap( // <2>
                        value -> Mono.fromCallable(() -> block(value)) // <3>
                                .subscribeOn(scheduler), // <4>
                        4) // <5>
                .collect(Result::new, Result::add) // <6>
                .doOnSuccess(Result::stop); // <7>

    }

    @RequestMapping("/serial")
    public Mono<Result> serial() {
        Scheduler scheduler = Schedulers.parallel();
        log.info("Handling /serial");
        return Flux.range(1, 10) // <1>
                .log() //
                .map( // <2>
                        this::block) // <3>
                .collect(Result::new, Result::add) // <4>
                .doOnSuccess(Result::stop) // <5>
                .subscribeOn(scheduler); // <6>
    }

    @RequestMapping("/netty")
    public Mono<Result> netty() {
        log.info("Handling /netty");
        return Flux.range(1, 10) // <1>
                .log() //
                .flatMap(this::fetch) // <2>
                .collect(Result::new, Result::add) //
                .doOnSuccess(Result::stop); // <3>

    }

    private HttpStatus block(int value) {
        return this.restTemplate.getForEntity(url, String.class, value).getStatusCode();
    }

    private Mono<HttpStatus> fetch(int value) {
        return this.client.perform(ClientWebRequestBuilders.get(url)).extract(ResponseExtractors.response(String.class))
                .map(response -> response.getStatusCode());
    }

    public static void main(String[] args) {
        // System.setProperty("reactor.io.epoll", "false");
        SpringApplication.run(ReactiveApplication.class, args);
    }

}

class Result {

    private ConcurrentMap<HttpStatus, AtomicLong> counts = new ConcurrentHashMap<>();

    private long timestamp = System.currentTimeMillis();

    private long duration;

    public long add(HttpStatus status) {
        AtomicLong value = this.counts.getOrDefault(status, new AtomicLong());
        this.counts.putIfAbsent(status, value);
        return value.incrementAndGet();
    }

    public void stop() {
        this.duration = System.currentTimeMillis() - this.timestamp;
    }

    public long getDuration() {
        return this.duration;
    }

    public Map<HttpStatus, AtomicLong> getCounts() {
        return this.counts;
    }

}
