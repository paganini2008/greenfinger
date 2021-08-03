<div id="indexSummaryBox">
	<div class="indexSummary">
		<label>Start Time: </label>
		<span>${(summary.startTime?string('MMM,dd yyyy HH:mm:ss'))!}</span>
		<label>Completed: </label>
		<span>${(summary.completed?string)!}</span>
		<label>Elapsed Time: </label>
		<span>${(summary.elapsedTime)!}</span>
	</div>
	<div class="indexSummary">
		<label>Url Count: </label>
		<span>${(summary.urlCount)!}</span>
		<label>Existed Url Count: </label>
		<span>${(summary.existedUrlCount)!}</span>
		<label>Filtered Url Count: </label>
		<span>${(summary.filteredUrlCount)!}</span>
	</div>
	<div class="indexSummary">
		<label>Invalid Url Count: </label>
		<span>${(summary.invalidUrlCount)!}</span>
		<label>Saved Url Count: </label>
		<span>${(summary.savedCount)!}</span>
		<label>Indexed Url Count: </label>
		<span>${(summary.indexedCount)!}</span>
	</div>
</div>