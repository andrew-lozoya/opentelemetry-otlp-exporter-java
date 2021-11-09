package com.example;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;

import io.opentelemetry.api.metrics.MeterProvider;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.MetricReaderFactory;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.samplers.Sampler;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;

import io.opentelemetry.exporter.logging.LoggingSpanExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;

import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * All SDK management takes place here, away from the instrumentation code, which should only access
 * the OpenTelemetry APIs.
 */
public final class OTELConfiguration {

    private static final Resource serviceNameResource = Resource.create(Attributes.of(ResourceAttributes.SERVICE_NAME, "BasicHttpURLConnectionClient"));

    //gRPC New Relic Metadata
    public static Metadata metadata() {
        Metadata metadata = new Metadata();
        // Inject context into the gRPC request metadata
        metadata.put(Metadata.Key.of("api-key", Metadata.ASCII_STRING_MARSHALLER), System.getenv("APIKEY"));
        return metadata;
    }

    public static ManagedChannel newrelicChannel() {
        return ManagedChannelBuilder.forAddress("otlp.nr-data.net", 4317)
                .useTransportSecurity() // HTTPS
                .intercept(MetadataUtils.newAttachHeadersInterceptor(metadata()))
                .build();
    }

    public static OtlpGrpcSpanExporter spanExporter(boolean gRPC) {
        if (gRPC) {
            return OtlpGrpcSpanExporter.builder()
                    .setChannel(newrelicChannel()) // gRPC New Relic Channel
                    .setTimeout(30, TimeUnit.SECONDS) // set the max amount of time an export can run before getting interrupted
                    .build();
        } else {
            return OtlpGrpcSpanExporter.builder()
                    .setEndpoint("https://otlp.nr-data.net:4317") // New Relic's OTLP endpoint supports HTTP/2 & gRPC
                    .addHeader("api-key", System.getenv("APIKEY"))
                    .setTimeout(30, TimeUnit.SECONDS) // set the max amount of time an export can run before getting interrupted
                    .build();
        }
    }
    /**
     * Adds a SimpleSpanProcessor initialized with OtlpGrpcSpanExporter to the TracerSdkProvider.
     *
     * @return a ready-to-use {@link OpenTelemetry} instance.
     */
    public static OpenTelemetry initOpenTelemetry(boolean gRPC) {

        SdkTracerProvider sdkTracerProvider = SdkTracerProvider.builder()
                .setSampler(Sampler.traceIdRatioBased(1.0)) //  Configurable percentage of traces, and additionally samples any trace that was sampled upstream.
                .setResource(Resource.getDefault().merge(serviceNameResource))
                .addSpanProcessor(SimpleSpanProcessor.create(new LoggingSpanExporter()))
                // LoggingSpanExporter prints spans to the console.
                // OpenTelemetry offers 3 different default span processors:
                //   - SimpleSpanProcessor
                //   - BatchSpanProcessor
                //   - MultiSpanProcessor <- this is a container for other SpanProcessors
                //     |-- SimpleSpanProcessor
                //     |-- BatchSpanProcessor
                .addSpanProcessor(SimpleSpanProcessor.create(spanExporter(gRPC)))
                .build();


        OpenTelemetrySdk openTelemetrySdk = OpenTelemetrySdk.builder()
                .setTracerProvider(sdkTracerProvider)
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .buildAndRegisterGlobal();

        Runtime.getRuntime().addShutdownHook(new Thread(sdkTracerProvider::shutdown));
        return openTelemetrySdk;

    }

    public static OtlpGrpcMetricExporter metricExporter(boolean gRPC) {
        // set up the metric exporter and wire it into the SDK and a timed reader.
        if (gRPC) {
            return OtlpGrpcMetricExporter.builder()
                    .setChannel(newrelicChannel()) // gRPC New Relic Channel
                    .setTimeout(30, TimeUnit.SECONDS) // set the max amount of time an export can run before getting interrupted
                    .build();
        } else {
            return OtlpGrpcMetricExporter.builder()
                    .setEndpoint("https://otlp.nr-data.net:4317") // New Relic's OTLP endpoint supports HTTP/2 & gRPC
                    .addHeader("api-key", System.getenv("APIKEY"))
                    .setTimeout(30, TimeUnit.SECONDS) // set the max amount of time an export can run before getting interrupted
                    .build();
        }
    }
    /**
     * Initializes a Metrics SDK with a OtlpGrpcMetricExporter and an IntervalMetricReader.
     *
     * @return a ready-to-use {@link MeterProvider} instance
     */
    public static MeterProvider initOpenTelemetryMetrics(boolean gRPC) {
        MetricReaderFactory periodicReaderFactory =
                PeriodicMetricReader.create(metricExporter(gRPC), Duration.ofMillis(1000));

        SdkMeterProvider sdkMeterProvider = SdkMeterProvider.builder()
                .registerMetricReader(periodicReaderFactory)
                .setResource(Resource.getDefault().merge(serviceNameResource))
                .buildAndRegisterGlobal();

        Runtime.getRuntime().addShutdownHook(new Thread(sdkMeterProvider::shutdown));
        return sdkMeterProvider;
    }
}