def call(Map iminfo) {
    String str = ""
    String dbgComment = ""
    reportUsage(getClass().protectionDomain.codeSource.location.path)

    if (!iminfo) {
        return "NONE"
    }

    if (iminfo.containsKey("dbgComment")) {
        dbgComment = iminfo["dbgComment"]
    } else {
        dbgComment = "Docker image iminfo map"
    }
    str = str + "    " + dbgComment + "\n\n"

    // registry to use
    str = str + "    Registry host: " + iminfo["registryHost"] + "\n"
    str = str + "    Registry URL: " + iminfo["registryUrl"] + "\n"
    str = str + "    Registry credentialsId: " + iminfo["credentialsId"] + "\n"
    str = str + "\n"

    str = str + "    Image ID: " + iminfo["imageID"] + "\n"
    str = str + "    Current tag: " + iminfo["tag"] + "\n"

    if (iminfo.containsKey("prevTags")) {
        str = str + "    Previous tags:\n\n"
        for (item in iminfo["prevTags"]) {
            str = str + "        " + item + "\n"
        }
        str = str + "\n"
    }

    if (iminfo.containsKey("extraTags")) {
        str = str + "    Extra tags:\n\n"
        for (item in iminfo["extraTags"]) {
            str = str + "        " + item + "\n"
        }
        str = str + "\n"
    }

    str = str + "    END: " + dbgComment + "\n"
    return str
}
