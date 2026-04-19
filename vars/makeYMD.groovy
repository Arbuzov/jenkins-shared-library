String call(String delimeter = '/') {
  reportUsage(getClass().protectionDomain.codeSource.location.path)
  return (new Date()).format("yyyy${delimeter}MM${delimeter}dd")
}