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
</head>
<style type="text/css">

    #searchBox{
    	height: 60px;
    	width: 100%;
    	clear: both;
    }

	#tabBox {
		height: auto;
		width: 100%;
		position: relative;
		bottom: 5px;
	}
	    
	
	#tabContent{
		height: auto;
	}
	
	#saveBtn{
		width: calc(100% - 10px);
		height: 36px;
		line-height: 36px;
		padding: 5px auto;
		cursor: pointer;
		text-align: center;
		font-weight: 800;
		float: left;
		display: inline-block;
		margin: 10px auto;
		background-color: #97CBFF;
	}
	    
</style>
<script type="text/javascript">
	$(function(){
	
		autoLoad();
		setInterval(autoLoad, 5000);
		
	});
	
	function autoLoad(){
		$('#searchForm').attr('action','list');
		var obj = $(this);
		var url = '${contextPath}/catalog/${(catalogId)!}/summary/content';
			$.ajax({
			    url: url,
				type:'post',
				dataType:'html',
				success: function(data){
				    $('#tabBox').html(data);
				}
			});
	}
	
</script>
<body>
		<#include "top.ftl">
		<div id="container">
			<div id="searchBox">
			</div>
			<div id="tabBox">
			</div>
		</div>
		<#include "foot.ftl">
</body>
</html>