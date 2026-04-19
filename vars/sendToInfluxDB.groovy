def call(Map args = [:]){
    def influxDBURL = 'http://192.168.67.55:8086/write?db=devops'
    String measurement = args.measurement ?: "jenkins_default_metric"
    Map tags = args.tags ?: [project: env.JOB_NAME ?: 'unknown']
    Map fields = args.fields ?: [value: 1]
    def tagSet = tags.collect { k,v -> "${k}=${v}" }.join(',')
    def fieldSet = fields.collect { k,v -> "${k}=${v}" }.join(',')
    def line = tagSet ? "${measurement},${tagSet} ${fieldSet}" : "${measurement} ${fieldSet}"
    try{
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
