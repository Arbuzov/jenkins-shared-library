import org.apache.commons.io.FilenameUtils

def call(String callerFilePath = "") {
    try {
        callerFunctionName = FilenameUtils.getBaseName(callerFilePath)
        envString = env.getEnvironment().collect { key, value ->
           "${key}=\"${value.replaceAll('"','')}\""
        }.join(',')
        if (callerFunctionName) {
            def influxDBURL = 'http://192.168.67.55:8086/write?db=devops'
            def influxDBData = "shared_call,function=${callerFunctionName},project=${env.JOB_NAME} value=1,${envString}"
            httpRequest(
                url: influxDBURL, 
                contentType: 'APPLICATION_FORM', 
                httpMode: 'POST', 
                requestBody: influxDBData,
            )
            echo "Data sent to InfluxDB: Function: ${callerFunctionName}, Project: ${env.JOB_NAME}"
        }
    } catch (Exception e) {
        echo "Failed to send data to InfluxDB: ${e.message}. Continuing execution."
    }
}