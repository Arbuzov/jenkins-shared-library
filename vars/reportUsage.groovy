import org.apache.commons.io.FilenameUtils

def call(Map args = [:]) {
    String path = args.path ?: ''
    String function = FilenameUtils.getBaseName(path)
    if (!function) {
        return
    }
    Map extraTags = args.findAll { it.key != 'path' && it.value != null }
    Map envFields = env.getEnvironment().collectEntries { key, value ->
        [(key): "\"${value.replaceAll('"','')}\""]
    }
    sendToInfluxDB(
        measurement: "shared_call",
        tags: extraTags + [
            function: function,
            project : env.JOB_NAME
        ],
        fields: [value: 1] + envFields
    )
}
