package com.example;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributeKey;

import io.opentelemetry.context.Scope;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapSetter;

import io.opentelemetry.api.metrics.MeterProvider;
import io.opentelemetry.api.metrics.Meter;

import java.io.BufferedReader;
import java.io.InputStreamReader;

import java.net.*;
import java.nio.charset.Charset;

public class BasicHttpURLConnectionClient {
    // it is important to initialize your SDKs as early as possible in your application's lifecycle
    private static final OpenTelemetry openTelemetry =
            OTELConfiguration.initOpenTelemetry(false);

    private static final MeterProvider meterProvider =
            OTELConfiguration.initOpenTelemetryMetrics(false);

    // OTEL Specification calls to name your Tracer / Meter after the class it instruments
    private static final Tracer tracer =
            openTelemetry.getTracer("io.opentelemetry.OtlpTracer", "0.9.1");

    private static final Meter meter =
            meterProvider.get("io.opentelemetry.OtlpMeter");

    // Implicitly tell OpenTelemetry to inject the context in the HTTP headers
    private static final TextMapSetter<HttpURLConnection> setter =
            URLConnection::setRequestProperty;

    private static void HttpClient() throws Exception {
        int status = 0;
        StringBuilder content = new StringBuilder();

        // This project supports a local server listening on port 8080
        URL url = new URL("http://localhost:8080");
        Span parentSpan = tracer.spanBuilder("/demo/ResponseServlet").setSpanKind(SpanKind.CLIENT).startSpan();
        try (Scope ignored =  parentSpan.makeCurrent()) {
            parentSpan.setAttribute("component", "http");
            parentSpan.setAttribute("http.method", "GET");
            /*
              Only one of the following is required:
                - http.url
                - http.scheme, http.host, http.target
                - http.scheme, peer.hostname, peer.port, http.target
                - http.scheme, peer.ip, peer.port, http.target
             */
            parentSpan.setAttribute("http.url", url.toString());

            HttpURLConnection transportLayer = (HttpURLConnection) url.openConnection();

            // Inject the request with the *current*  Context, which contains our current Span.
            openTelemetry.getPropagators().getTextMapPropagator().inject(Context.current(), transportLayer, setter);

            // Do Work
            try {
                // Connect to sever
                transportLayer.setRequestMethod("GET");
                status = transportLayer.getResponseCode();

                // Process the request from the BufferReader
                doSomeWork(transportLayer, content);

                parentSpan.setStatus(StatusCode.OK);
            } catch (Throwable e) {
                parentSpan.setStatus(StatusCode.ERROR, "HTTP Code: " + status);
            }
        } finally {
            System.out.println();
            // Output the result of the request
            System.out.println("Response Code: " + status);
            System.out.println("Response Msg: " + content);
            System.out.println();
            // Close the Span
            System.out.println("Parent Span:");
            parentSpan.end();
        }
    }

    private static void doSomeWork(HttpURLConnection transportLayer, StringBuilder content) throws InterruptedException {
        Thread.sleep(100);
        Span childSpan = tracer.spanBuilder("BufferedReader/java.io.InputStream/getInputStream")
                // The OpenTelemetry API offers also an automated way to propagate the parent span on the current thread:
                // NOTE: setParent(...) is not required any more
                // `Span.current()` is automatically added as parent
                .startSpan();
        try (Scope ignored = childSpan.makeCurrent()) {
            //Do child work
            BufferedReader in =
                    new BufferedReader(
                            new InputStreamReader(transportLayer.getInputStream(), Charset.defaultCharset()));
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                content.append(inputLine);
            }
            // Span Event
            Attributes eventAttributes = Attributes.of(
                    AttributeKey.longKey("result"), transportLayer.getContentLengthLong());

            childSpan.addEvent("bufferedReaderLength", eventAttributes);

            childSpan.setStatus(StatusCode.OK);
        } catch (Throwable e) {
            childSpan.setStatus(StatusCode.ERROR);
        } finally {
            System.out.println();
            System.out.println("Child Span:");
            // Close the Span
            childSpan.end();
            Thread.sleep(100);
        }
    }

    public static void main(String[] args) {
        meter.gaugeBuilder("jvm.memory.freeMemory").setDescription("Reports JVM memory usage.").setUnit("byte").buildWithCallback(
                result -> result.observe(Runtime.getRuntime().freeMemory(), Attributes.empty()));
        // Perform request every 10s
        Thread loop =
                new Thread(() -> {
                    while (true) {
                        try {
                            HttpClient();
                            Thread.sleep(10000);
                            System.out.println();
                        } catch (Exception e) {
                            System.out.println(e.getMessage());
                        }
                    }

                });
        loop.start();
    }
}