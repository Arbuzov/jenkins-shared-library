def call(String summaryText, String header = '', String icon = 'folder.gif') {
  reportUsage(path: getClass().protectionDomain.codeSource.location.path)
  def summary=manager.createSummary(icon);
  if (!header.equals('')) {
    summary.appendText("<h2>${header}</h2>", false)
  }
  summary.appendText(summaryText, false)
  return summary
}

/*
 * Overloaded method to accept parameters as a map
 * Function call with named parameters is deprecated but still 
 * supported for backward compatibility
 */
def call(Map arg) {
  def summaryText = arg.summaryText ?: ''
  def header = arg.header ?: ''
  def icon = arg.icon ?: 'folder.gif'
  return this.call(summaryText, header, icon)
}
