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
		lastSuccess()
		lastFailure()
		lastDuration()
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
		lastSuccess()
		lastFailure()
		lastDuration()
		buildButton()
	}
}

categorizedJobsView("GPS-Ref") {
	jobs {
		regex(/^(u-boot-2009.01-build.*|gpsdo-source-reports.*|openwrt-.*-AA.*|alephgx-prodstation-pack.*)$/)
	}
	columns {
		status()
		weather()
		name()
		lastSuccess()
		lastFailure()
		lastDuration()
		buildButton()
	}
}