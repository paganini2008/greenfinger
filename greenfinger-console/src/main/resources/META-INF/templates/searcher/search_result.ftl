<#import "../common/page.ftl" as pageToolbar>
<script type="text/javascript">
	$(function(){
		
	})
</script>
<div id="tabContent">
	<#if page ?? && page.results?? && page.results? size gt 0>
		<#list page.results as bean>
			<div class="search-result">
				<p class="search-result-title">
					<a href="${(bean.path)!}" target="_blank">${(bean.title)!}</a>
				</p>
				<p class="search-result-content">
					${(bean.content)!}
				</p>
				<p class="search-result-catalog">
					【${(bean.cat)!}】
					<a href="${(bean.url?html)!}" target="_blank">${(bean.catalog)!}</a>
					<font color="#9D9D9D">(Last Modified:&nbsp;${(bean.createTime?string('MMM,dd yyyy HH:mm:ss'))!}, Version:&nbsp;${(bean.version)!})</font>
				</p>
			</div>
		</#list>
	</#if>
</div>
<#if page ?? && page.results?? && page.results? size gt 0>
	<@pageToolbar.page page = page display = 0/> 
</#if>