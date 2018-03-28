categorizedJobsView("RT-BSRC") {
	jobs {
		regex(/^(BSRC-AlephG|BSRC-AlephP|BSRC-TR800)-.*$/)
	}
	categorizationCriteria {
		regexGroupingRule(/(BSRC-AlephG|BSRC-AlephP|BSRC-TR800)/)
	}
	columns {
		status()
		weather()
		name()
		buildButton()
	}
}
categorizedJobsView("RT-Router") {
	jobs {
		regex(/^(u-boot-2009.01-build.*|rtems-.*|rt-router-.*)$/)
	}
	columns {
		status()
		weather()
		name()
		buildButton()
	}
}