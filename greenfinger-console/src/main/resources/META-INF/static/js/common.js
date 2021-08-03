$(function() {

	TxtBoxUtils.start();
	PopBoxUtils.start();

});

var TableUtils = {
	rowColour : function() {
		$('.tblCom tbody tr:odd').css('background-color', '#F0F0F0');
	},

	initialize : function(line) {
		var lastTr = $('.tblCom tbody tr:last');
		if (lastTr.find('.tabNoData').length > 0) {
			return;
		}
		var len = $('.tblCom tbody tr').length;
		if (len < line) {
			var ht = '<tr>';
			lastTr.find('td').each(function() {
				ht += '<td></td>';
			});
			ht += '</tr>';
			for ( var i = 1; i <= line - len; i++) {
				$(ht).appendTo($('.tblCom tbody'));
			}
			$('.tblCom tbody tr:odd').css('background-color', '#F0F0F0');
		}
	},
	
	initializeMany : function(tblIndex, line) {
		var lastTr = $('.tblCom').eq(tblIndex).find('tbody tr:last');
		if (lastTr.find('.tabNoData').length > 0) {
			return;
		}
		var len = $('.tblCom').eq(tblIndex).find('tbody tr').length;
		if (len < line) {
			var ht = '<tr>';
			lastTr.find('td').each(function() {
				ht += '<td></td>';
			});
			ht += '</tr>';
			for (var i = 1; i <= line - len; i++) {
				$(ht).appendTo($('.tblCom').eq(tblIndex).find('tbody'));
			}
			$('.tblCom').eq(tblIndex).find('tbody tr:odd').css('background-color', '#F0F0F0');
		}
	}
};

var TxtBoxUtils = {

	start : function() {
		$('.txtField').live('focus', function() {
			$(this).css('background-color', '#FFD2D2');
		}).live('blur', function() {
			$(this).css('background-color', '#fff');
		});
	}

};

var TxtUtils = {
	showLongString : function() {
		var width;
		$('.longName').each(function() {
			width = $(this).parent().width();
			$(this).css('width', width - 10);
			$(this).attr('title', $.trim($(this).text()));
		});
	},

	loadingString : function() {
		var tw;
		if ($('.tblCom').length > 0) {
			tw = $('.tblCom').height();
		} else {
			tw = 450;
		}
		var s = '<div class="waitForData" style="height: '
				+ tw
				+ 'px; line-height: '
				+ tw
				+ 'px;"><label>l</label><label>o</label><label>a</label><label>d</label><label>i</label><label>n</label><label>g</label>...</div>';
		$('#tabContent').html(s);
		var index = 0;
		var len = $('.waitForData label').length;
		var obj = setInterval(function() {
			$('.waitForData label').eq(index++ % len).css({
				'font-size' : '72pt',
				'text-transform' : 'uppercase'
			}).siblings().css({
				'font-size' : '36pt',
				'text-transform' : 'lowercase'
			});
			if ($('.waitForData').length == 0) {
				clearInterval(obj);
			}
		}, 300);
	}
};

var PopBoxUtils = {

	start : function() {
		if ($(".popBox").length > 0) {
			$(".popBox").corner("round 16px");
			$(".popBox").hide();
			$(".popClose").click(function() {
				$(".popBox").CloseDiv();
			});
		}
	}

};

var DateUtils = {

	formatNow : function() {
		var days = [ '日', '一', '二', '三', '四', '五', '六' ];
		var obj = new Date();
		var y = obj.getFullYear();
		var m = obj.getMonth() + 1;
		var d = obj.getDate();
		var i = obj.getDay();
		return y + '年' + m + '月' + d + '日' + ' 星期' + days[i];
	},

	toElapsedArray : function(ms) {
		var s = 1000;
		var m = s * 60;
		var h = m * 60;
		var d = h * 24;
		var day = parseInt(ms / d);
		var hour = parseInt((ms - day * d) / h);
		var minute = parseInt((ms - day * d - hour * h) / m);
		var second = parseInt((ms - day * d - hour * h - minute * m) / s);
		var millisecond = parseInt(ms - day * d - hour * h - minute * m
				- second * s);
		var result = [];
		result.push(day);
		result.push(hour);
		result.push(minute);
		result.push(second);
		result.push(millisecond);
		return result;
	},

	toElapsedFullString : function(ms) {
		var array = DateUtils.toElapsedArray(ms);
		var strArray = [];
		for ( var i = 0; i < 5; i++) {
			strArray[i] = array[i] >= 10 ? "" + array[i] : "0" + array[i];
		}
		strArray[4] = array[4] >= 100 ? strArray[4] : "0" + strArray[4];
		return strArray[0] + "天:" + strArray[1] + "小时:" + strArray[2] + "分钟:"
				+ strArray[3] + "秒";
	}

};

var Utils = {
	getScrollTop : function() {
		if ($.browser.mozilla) {
			return document.documentElement.scrollTop;
		} else {
			return document.body.scrollTop;
		}
	}
};

// 对Date的扩展，将 Date 转化为指定格式的String
// 月(M)、日(d)、小时(h)、分(m)、秒(s)、季度(q) 可以用 1-2 个占位符，
// 年(y)可以用 1-4 个占位符，毫秒(S)只能用 1 个占位符(是 1-3 位的数字)
// 例子：
// (new Date()).format("yyyy-MM-dd hh:mm:ss.S") ==> 2006-07-02 08:09:04.423
// (new Date()).format("yyyy-M-d h:m:s.S") ==> 2006-7-2 8:9:4.18
Date.prototype.format = function(fmt) { // author: meizz
	var o = {
		"M+" : this.getMonth() + 1, // 月份
		"d+" : this.getDate(), // 日
		"h+" : this.getHours(), // 小时
		"m+" : this.getMinutes(), // 分
		"s+" : this.getSeconds(), // 秒
		"q+" : Math.floor((this.getMonth() + 3) / 3), // 季度
		"S" : this.getMilliseconds()
	// 毫秒
	};
	if (/(y+)/.test(fmt))
		fmt = fmt.replace(RegExp.$1, (this.getFullYear() + "")
				.substr(4 - RegExp.$1.length));
	for ( var k in o)
		if (new RegExp("(" + k + ")").test(fmt))
			fmt = fmt.replace(RegExp.$1, (RegExp.$1.length == 1) ? (o[k])
					: (("00" + o[k]).substr(("" + o[k]).length)));
	return fmt;
};