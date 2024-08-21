// This suppose to be deprecated
def call(String companyName, String apiToken, String message, String header="") {
    reportUsage(getClass().protectionDomain.codeSource.location.path)
    String sendMessage = ((!header.equals("")?"**${header.replace('%2F', ' ')}** \n":"")+message.replace('%2F', ' '));
    httpRequest(
        url : "https://${companyName}.ryver.com/application/webhook/${apiToken}",
        httpMode: 'POST',
        consoleLogResponseBody: false,
        contentType : 'TEXT_PLAIN',
        requestBody: sendMessage
    )
}
