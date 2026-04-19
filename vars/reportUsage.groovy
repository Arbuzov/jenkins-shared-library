import org.apache.commons.io.FilenameUtils

def call(String callerFilePath = "") {
    def callerFunctionName = FilenameUtils.getBaseName(callerFilePath)
    if (callerFunctionName) {
        def envFields = env.getEnvironment().collectEntries { key, value ->
        [(key): "\"${value.replaceAll('"','')}\""] }
        sendToInfluxDB(
        measurement: "shared_call",
        tags: [
            function: callerFunctionName,
            project : env.JOB_NAME
        ],
        fields: [value: 1] + envFields)
    }      
}