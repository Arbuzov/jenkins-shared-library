/*
 *
 */
String call(Throwable throwable) {
    reportUsage(path: getClass().protectionDomain.codeSource.location.path)
    final StringWriter sw = new StringWriter();
    final PrintWriter pw = new PrintWriter(sw, true);
    throwable.printStackTrace(pw);
    return sw.getBuffer().toString();
}