def call(Map args = [:]) {
    def influxDBURL = args.url ?: env.INFLUXDB_WRITE_URL
    if (!influxDBURL) {
        // Telemetry is best-effort: stay silent when no endpoint is configured
        // so the step is safe to call from any pipeline.
        return
    }

    String measurement = args.measurement ?: 'jenkins_default_metric'
    Map tags = args.tags ?: [project: env.JOB_NAME ?: 'unknown']
    Map fields = args.fields ?: [value: 1]
    def tagSet = tags.collect { k, v -> "${k}=${v}" }.join(',')
    def fieldSet = fields.collect { k, v -> "${k}=${v}" }.join(',')
    def line = tagSet ? "${measurement},${tagSet} ${fieldSet}" : "${measurement} ${fieldSet}"

    try {
        httpRequest(
            url: influxDBURL,
            contentType: 'APPLICATION_FORM',
            httpMode: 'POST',
            requestBody: line,
            quiet: true
        )
        echo "Data sent to InfluxDB: ${tagSet}"
    } catch (Exception e) {
        echo "Failed to send data to InfluxDB: ${e.message}. Continuing execution."
    }
}
