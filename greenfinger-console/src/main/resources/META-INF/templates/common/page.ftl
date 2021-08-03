<#setting number_format="#">
<#macro page page display>
<style style="text/css">
	#pageULBox{
		width: 100%;
		height: 50px;
		clear: both;
		background-color: #F0F0F0;
	}

	.pageUL {
		float: right;
		display: inline-block;
		margin-right: 10px;
		height: 32px;
		width: auto;
		margin: 10px auto;
	}
	
	.pageUL li {
		list-style: none;
		float: left;
		display: inline-block;
		line-height: 32px;
		height: 32px;
		width: auto;
		text-align: center;
		margin: 0px 3px;
		font-weight: 800;
	}
	
	.pageSummary{
		color: #3C3C3C;
	}
	
	.pageAction {
		width: 45px;
	}
	
	.pageNumber {
		width: 20px;
	}
	
	.pageShow {
		width: 30px;
	}
	
</style>
<div id="pageULBox">
	<ul class="pageUL">
		  <li class="pageSummary">
		  		<font>Current Records:&nbsp;</font>${page.results?size}&nbsp;&nbsp;
		  		<font>Total Records:&nbsp;</font>${page.rows}&nbsp;&nbsp;
		  		<font>Total Pages:&nbsp;</font>${page.totalPages}&nbsp;&nbsp;
		  </li>
		  <li class="pageAction">
		  		<a href="javascript:void(0);" onclick="javascript:goToPage(${page.firstPage})">First Page</a>&nbsp;|
		  </li>
	      <li class="pageAction">
	      		<a id="pageForPrev" href="javascript:void(0);" onclick="javascript:goToPage(${page.previousPage})">Previous Page</a>&nbsp;|
	      </li>
		  <#if page.pageNos?? && page.pageNos?size gt 0>
		      <#list page.pageNos as pageNo>
		      		<li class="pageNumber">
		      			<#if pageNo == page.page>
		      				${pageNo}
		      			<#else>
		      				<a href="javascript:void(0);" onclick="javascript:goToPage(${pageNo})">${pageNo}</a>
		      			</#if>
		      		</li>
			  </#list>
		  </#if>
		  <#if page.page != page.totalPages>
		       <li class="pageAction">
		       		<a id="pageForNext" href="javascript:void(0);" onclick="javascript:goToPage(${page.nextPage})">Next Page</a>
		       		&nbsp;|
		       </li>
	      </#if>
	       <li class="pageAction">
	       		<a href="javascript:void(0);" onclick="javascript:goToPage(${page.totalPages})">Last Page</a>
	       </li>
	       <li>
	       		<input type="text" value="${page.page}" id="pageNoValue" style="width:40px;padding-left: 3px;"/>
	       </li>
	       <li class="pageGo">
	       		<input onclick="javascript:goToPage(-1)" type="button" value="Go" style="width: 60px; padding: 2px 10px; cursor: pointer;font-weight: 800; background-color: #97CBFF;"/>
	       </li>
	       <#if display == 1>
	       		<li class="pageShow"> <a <#if page.size == 10>class="hoverLink" </#if> href="javascript:void(0);" onclick="javascript:setPageSize(20);"><span>20 Rows</span></a></li>
	            <li class="pageShow"> <a <#if page.size == 50>class="hoverLink" </#if> href="javascript:void(0);" onclick="javascript:setPageSize(50);"><span>50 Rows</span></a></li>
	            <li class="pageShow"> <a <#if page.size == 100>class="hoverLink" </#if> href="javascript:void(0);" onclick="javascript:setPageSize(100);"><span>100 Rows</span></a></li>
		   </#if>
	</ul>
</div>
<script type="text/javascript">

	function setPageSize(pageSize){
	    var date = new Date();   
	    date.setTime(date.getTime() + (30 * 24 * 60 * 60 * 1000));
		document.cookie = "DATA_LIST_SIZE=" + pageSize + ";expires=" + date.toGMTString() + ";path=/";
		$('#pageNo').val(1);
		$(".pageForm").submit();
	}
	
	function goToPage(pageNo) {
		if(pageNo == -1){
			pageNo = $('#pageNoValue').val();
			if(pageNo == 0){
				pageNo = 1;
			}
		}
		var currentPageNo = ${page.page};
		if(pageNo == currentPageNo) {
			return;
		}
		var totalPages = ${page.totalPages};
		if(eval(pageNo) > eval(totalPages)){
			pageNo = totalPages;
		}
		if(pageNo == 0){
			pageNo = 1;
		}
		$("#pageNo").val(pageNo);
		$(".pageForm").submit();
	}
</script> 
</#macro>