import org.apache.commons.io.FilenameUtils

def call(Map args = [:]) {
    String path = args.path ?: ''
    String function = FilenameUtils.getBaseName(path)
    if (!function) {
        return
    }
    if (args.deprecated) {
        // Only a step name names a replacement, any other truthy value is a plain flag
        String replacement = args.deprecated instanceof CharSequence
            ? ", use ${args.deprecated}() instead"
            : ''
        echo "WARNING: ${function}() is deprecated${replacement}"
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
