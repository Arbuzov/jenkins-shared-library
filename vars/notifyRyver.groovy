def call(String companyName, String apiToken, String message, String header="") {
    String sendMessage = ((!header.equals("")?"**${header.replace('%2F', ' ')}** \n":"")+message.replace('%2F', ' '));
    httpRequest(
        url : "https://${companyName}.ryver.com/application/webhook/${apiToken}",
        httpMode: 'POST',
        consoleLogResponseBody: false,
        contentType : 'TEXT_PLAIN',
        requestBody: sendMessage
    )
}
