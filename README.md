[![Example Code header](https://github.com/newrelic/opensource-website/raw/master/src/images/categories/Example_Code.png)](https://opensource.newrelic.com/oss-category/#example-code)

# opentelemetry-otlp-exporter-java

In this repo, we focus on how to configure an OTLP exporter in a simple observable service that will export data directly to New Relic's OTLP endpoint.

![](https://docs.newrelic.com/44724d5e137a64a9b9f426e1bcddc445/native_otlp.svg)

However, As with any technology, in order to get to grips with OpenTelemetry there is a small amount of terminology that it is useful to know:

Distributed Terminology:
* Trace: a record of activity for a request through a distributed system. A trace is a [Directed Acyclic Graph](https://en.wikipedia.org/wiki/Directed_acyclic_graph%5C) of spans.
* Spans: named, timed operations representing a single operation within a trace. Spans can be nested to form a trace tree. Each trace contains a root span, which typically describes the end-to-end latency and (optionally) one or more sub-spans for its sub-operations.
* Metrics: a raw measurement about a service, captured at runtime. OpenTelemetry defines three metric instruments — counter, measure and observer. An observer supports an asynchronous API collecting metric data on-demand, once per collection interval.
* Context: a span contains a span context, which is a set of globally unique identifiers that represent the unique request that each span is a part of, representing the data required for moving trace information across service boundaries. OpenTelemetry also supports the correlation context which can carry any user-defined properties. Correlation context is not required and components may choose not to carry or store this information.
* Context Propagation: the means by which context is bundled and transferred between services, typically via HTTP headers. Context propagation is a key part of the OpenTelemetry system, and has some interesting use cases beyond tracing — for example when doing A/B testing. Note that OpenTelemetry supports multiple protocols for context propagation and to avoid issues, it is important that you use a single method throughout your application. So for example, if you use the W3C specification in one service, you need to use it everywhere in your system. 

OpenTelemetry SDK Terminology:
* TraceProvider: is the entry point of API. It provides access to Tracers.
  * Stateful object holding configuration with a global provider while supporting additional ones.
* Tracer: is the class responsible for the generation of Spans
  * A Span is a named, timed operation that represents a contiguous segment of work in a trace.
  * Delegates getting active Span and making a given Span as active to the SpanContext.
* Span: is the API to trace an operation
  * A span represents an operation within a transaction. Each Span encapsulates at minimum the following data:
  * An operation name
  * A start and finish timestamp
  * Attributes: A list of key-value pairs.
* Parent's Span identifier.
  * SpanContext information required to reference a Span.
* SpanContext: represent the serialized and propagated portion of a Span.
  * Represents all the information that identifies Span in the Trace and MUST be propagated to child Spans and across process boundaries.

Extra reading content:
  * SUPER IMPORTANT! [W3C Trace-Context HTTP Propagator](https://w3c.github.io/trace-context/)
  * [W3C Correlation-Context HTTP Propagator](https://w3c.github.io/correlation-context/)
  * [OpenTelemetry OTPL Protocol](https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/protocol/otlp.md)
  * [New Relic OpenTelemetry Quick Start](https://docs.newrelic.com/docs/more-integrations/open-source-telemetry-integrations/opentelemetry/opentelemetry-quick-start/)


![](https://github.com/andrew-lozoya/opentelemetry-jeager-exporter-java/blob/main/Resources/2020-10-10_13-36-25.png)
