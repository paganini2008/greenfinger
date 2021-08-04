<#setting number_format="#">
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<title>Greenfinger Catalog Editor</title>
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
	
	#catalogInfoBox {
		width: 80%;
		height: 100%;
		clear: both;
		margin: 0px auto;
		overflow-y: scroll;
		overflow-x: hidden;
	}
	
	.catalogInfo {
		width: 100%;
		height: 32px;
		clear: both;
		margin: 5px auto;
	}
	
	.catalogInfo p {
		width: 100%;
		height: 32px;
		display: block;
	}
	
	.catalogInfo p label{
		width: 120px;
		height: 32px;
		line-height: 32px;
		text-align: left;
		font-weight: 800;
		display: inline-block;
		float: left;
	}
	
	.catalogInfo p input[type="text"]{
		width: calc(100% - 20px);
		height: 32px;
		line-height: 32px;
		text-align: left;
		padding-left: 5px;
		display: inline-block;
		float: left;
	}
    
	
	#saveBtn{
		float: right;
		margin-right: 20px;
	}
	    
</style>
<script type="text/javascript">
	$(function(){
		$('#saveBtn').click(function(){
			var value = $.trim($('input[type="text"][name="name"]').val());
			if(value == null || value.length == 0){
				alert("Catalog's name must be required");
				return;
			}
			value = $.trim($('input[type="text"][name="cat"]').val());
			if(value == null || value.length == 0){
				alert("Catalog's category must be required");
				return;
			}
			value = $.trim($('input[type="text"][name="url"]').val());
			if(value == null || value.length == 0){
				alert("Catalog's url must be required");
				return;
			}
			value = $.trim($('input[type="text"][name="pathPattern"]').val());
			if(value == null || value.length == 0){
				alert("Catalog's Path Pattern must be required");
				return;
			}
			$('#catalogFrm').submit();
		});
	});
</script>
<body>
		<#include "top.ftl">
		<div id="container">
			<div id="catalogInfoBox">
				<form id="catalogFrm" action="${contextPath}/catalog/save" method="post">
					<input type="hidden" value="${(catalog.id)!}" name="id"/>
					<div class="catalogInfo">
						<p><label>Name: </label></p>
						<p><input type="text" name="name" value="${(catalog.name)!}"/></p>
					</div>
					<div class="catalogInfo">
						<p><label>Cat: </label></p>
						<p><input type="text" name="cat" value="${(catalog.cat)!}"/></p>
					</div>
					<div class="catalogInfo">
						<p><label>URL: </label></p>
						<p><input type="text" name="url" value="${(catalog.url)!}"/></p>
					</div>
					<div class="catalogInfo">
						<p><label>Page Encoding: </label></p>
						<p><input type="text" name="pageEncoding" value="${(catalog.pageEncoding)!}"/></p>
					</div>
					<div class="catalogInfo">
						<p><label>Path Pattern: </label></p>
						<p><input type="text" name="pathPattern" value="${(catalog.pathPattern)!}"/></p>
					</div>
					<div class="catalogInfo">
						<p><label>Excluded Path Pattern: </label></p>
						<p><input type="text" name="excludedPathPattern" value="${(catalog.excludedPathPattern)!}"/></p>
					</div>
					<div class="catalogInfo">
						<p><label>Max Fetch Size: </label></p>
						<p><input type="text" name="maxFetchSize" value="${(catalog.maxFetchSize)!100000}"/></p>
					</div>
					<div class="catalogInfo">
						<p><label>Duration: </label></p>
						<p><input type="text" name="duration" value="${(catalog.duration)!1200000}"/></p>
					</div>
					<div class="catalogInfo">
						<p><label>&nbsp;</label></p>
						<p><input class="cBtn" id="saveBtn" type="button" value="Save"/></p>
					</div>
				</form>
			</div>
		</div>
		<#include "foot.ftl">
</body>
</html>