/*
 *
 */
String call(Throwable throwable) {
    reportUsage(getClass().protectionDomain.codeSource.location.path)
    final StringWriter sw = new StringWriter();
    final PrintWriter pw = new PrintWriter(sw, true);
    throwable.printStackTrace(pw);
    return sw.getBuffer().toString();
}