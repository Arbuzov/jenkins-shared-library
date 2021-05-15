def call(String summaryText, String header = '', String icon = 'folder.gif') {
	def summary=manager.createSummary(icon);
	if (!header.equals('')) {
		summary.appendText("<h2>${header}</h2>", false)
	}
	summary.appendText(summaryText, false)
}
