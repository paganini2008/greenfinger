<#setting number_format="#">
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<title>Greenfinger Searcher</title>
<link rel="shortcut icon" href="#"/>
<script type="text/javascript">
	var $contextPath = '${contextPath}';
</script>
<link href="${contextPath}/static/css/base.css" rel="stylesheet" type="text/css" />
<script type="text/javascript" src="${contextPath}/static/js/lib/jquery-1.7.1.min.js"></script>
<script type="text/javascript" src="${contextPath}/static/js/lib/json2.js"></script>
<script type="text/javascript" src="${contextPath}/static/js/common.js"></script>
<script type="text/javascript" src="${contextPath}/static/js/lib/map.js"></script>
<script src="https://code.highcharts.com/highcharts.js"></script>
<script src="https://code.highcharts.com/modules/data.js"></script>
<script src="https://code.highcharts.com/modules/drilldown.js"></script>
<script src="https://code.highcharts.com/modules/exporting.js"></script>
<script src="https://code.highcharts.com/modules/export-data.js"></script>
<script src="https://code.highcharts.com/modules/accessibility.js"></script>
</head>
<style type="text/css">

	#summaryBox {
		height: 100px;
		width: 100%;
		margin: 50px auto;
		clear: both;
	}
	
	#chartBox{
    	height: 360px;
    	width: 100%;
    	clear: both;
    }
	    
</style>
<script type="text/javascript">
	$(function(){
	
		loadSummary();
		setInterval(loadSummary, 5000);
	});
	
	function loadSummary(){
		var url = '${contextPath}/catalog/${(catalogId)!}/summary/content';
			$.ajax({
			    url: url,
				type:'post',
				dataType:'html',
				success: function(data){
				    $('#summaryBox').html(data);
				}
			});
			
		 url = '${contextPath}/api/catalog/${(catalogId)!}/summary';
			$.ajax({
			    url: url,
				type:'post',
				dataType:'json',
				success: function(data){
					var summary = data.data;
				    loadChart(summary.urlCount,summary.existedUrlCount,summary.filteredUrlCount,summary.invalidUrlCount,summary.savedCount,summary.indexedCount);
				}
			});
	}
	
	var map = new Map();
	
	function loadChart(urlCount,existedUrlCount,filteredUrlCount,invalidUrlCount,savedCount,indexedCount){
		var divId = 'chartBox';
		if(map.containsKey(divId)){
			var chart = map.get(divId);
			chart.update({
				series: [{
			        name: 'URL Count Summary',
			        data: [
			            ['URL Count', urlCount],
			            ['Existed URL Count', existedUrlCount],
			            ['Filtered URL Count', filteredUrlCount],
			            ['Invalid URL Count', invalidUrlCount],
			            ['Saved URL Count', savedCount],
			            ['Indexed URL Count', indexedCount],
			        ],
			        dataLabels: {
			            enabled: true,
			            rotation: -90,
			            color: '#FFFFFF',
			            align: 'right',
			            y: 10, // 10 pixels down from the top
			            style: {
			                fontSize: '13px',
			                fontFamily: 'Arial, Helvetica, sans-serif'
			            }
			        }
			    }]
			});
		}else{
			var chart = Highcharts.chart(divId, {
			    chart: {
			        type: 'column'
			    },
			    title: {
			        text: '<b>Catalog Crawler Realtime Summary</b>'
			    },
			    xAxis: {
			        type: 'category',
			        labels: {
			            rotation: -45,
			            style: {
			                fontSize: '13px',
			                fontFamily: 'Arial, Helvetica, sans-serif'
			            }
			        }
			    },
			    yAxis: {
			        min: 0,
			        title: {
			            text: 'URL Count Summary'
			        }
			    },
			    legend: {
			        enabled: false
			    },
			    exporting: false,
			    tooltip: {
			        pointFormat: '<b>{point.y}</b>'
			    },
			    series: [{
			        name: 'URL Count Summary',
			        data: [
			            ['URL Count', urlCount],
			            ['Existed URL Count', existedUrlCount],
			            ['Filtered URL Count', filteredUrlCount],
			            ['Invalid URL Count', invalidUrlCount],
			            ['Saved URL Count', savedCount],
			            ['Indexed URL Count', indexedCount],
			        ],
			        dataLabels: {
			            enabled: true,
			            rotation: -90,
			            color: '#FFFFFF',
			            align: 'right',
			            y: 10, // 10 pixels down from the top
			            style: {
			                fontSize: '13px',
			                fontFamily: 'Arial, Helvetica, sans-serif'
			            }
			        }
			    }]
			});
			map.put(divId, chart);
		}
		
	}
	
</script>
<body>
		<#include "top.ftl">
		<div id="container">
			<div id="summaryBox">
			</div>
			<div id="chartBox">
			</div>
		</div>
		<#include "foot.ftl">
</body>
</html>