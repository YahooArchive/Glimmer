YUI.add('yui-pager', function (Y){
	
	function Pager (config) {
		Pager.superclass.constructor.apply(this, arguments);
	}
	
	Pager.Name = "Pager";
	
	var state = {
		callback: undefined,
		pageSize: 10,
		items: 0,
		page: 1,
		windowSize: 10,
		anchorClass: "pager-link",
		anchorClassCurrent: "pager-link-current",
		first: "&lt;&lt;first",
		previous: "&lt;previous",
		next: "next&gt;",
		last: "last&gt;&gt;",
		showingResults: "Showing results ",
		showingResultsTo: " to ",
		showingResultsOf: " of ",
		pagerElements: undefined,
		statusElements: undefined
	}
	
	function appendElement(element, innerHtml, targetPage, current) {
		var anchor = Y.Node.create("<a href='#'></a>");
		anchor.addClass(state.anchorClass);
		anchor.appendChild(innerHtml);
		if (current) {
			anchor.addClass(state.anchorClassCurrent)
		} else {
			anchor.on('click', function (e) {
				e.preventDefault();
				
				if (state.callback != undefined) {
					state.callback(targetPage, state);
				} else {
					alert(targetPage);
					this.setState({
						page: targetPage
					});
				} 
			});
		}
		
		element.appendChild(anchor);
	}
	
	function getRelativePageNumbers() {
		var a = new Array();
		a.push(0);
		
		var d = 1;
		
		var pages = getNumberOfPages();

		while (state.page - d >= 1 || state.page + d <= pages) {
			if (state.page - d >= 1) {
				a.unshift( -d);
			}
			if (state.page + d <= pages) {
				a.push(d);
			}
			if (d == 1) {
				d = 2;
			} else if (d == 2) {
				d = 5;
			} else if ( (100 * Math.log(d) / Math.LN10) % 100 == 0 ) {
				// power of 10.
				d *= 5;
			} else {
				d *= 2;
			}
		}
		
		return a;
	}
	
	function getNumberOfPages() {
		return Math.ceil(state.items / state.pageSize);
	}
	
	Y.extend(Pager, Y.Base, {
		initializer: function(config) { },
		destructor: function() { },
		
		setCallback: function(newCallback) {
			state.callback = newCallback;
		},
		
		setState: function(newState) {
			var changed = false;
			for (var key in state) {
				if (newState.hasOwnProperty(key)) {
					if (state[key] != newState[key]) {
						state[key] = newState[key];
						changed = true;
					}
				}
			}
			if (changed) {
				if (state.pagerElements != undefined) {
					for (var i in state.pagerElements) {
						this.renderPager(state.pagerElements[i]);
					}
				}
				if (state.statusElements != undefined) {
					for (var i in state.statusElements) {
						this.renderStatus(state.statusElements[i]);
					}
				}
			}
		},
		
		getState: function() {
			return state;
		},
		
		renderPager: function(pagerElementId) {
			var rootElement = Y.one(pagerElementId);
			rootElement.setContent("");
			var pages = getNumberOfPages();
			
			if (pages > 0) {
				if (state.page > 1) {
					appendElement(rootElement, state.first, 1);
					appendElement(rootElement, state.previous, state.page - 1);
				}
				var relativePageNumbers = getRelativePageNumbers();
				for (var i in relativePageNumbers) {
					var delta = relativePageNumbers[i];
					var label = delta == 0 ? state.page : (delta > 0) ? '+' + delta : delta;
					var pageNumber = state.page + delta;
					appendElement(rootElement, label, pageNumber, pageNumber == state.page);
				}
				
				if (state.page < pages) {
					appendElement(rootElement, state.next, state.page + 1);
					appendElement(rootElement, state.last, pages);
				}
			}
		},
		
		renderStatus: function(statusElementId) {
			var pages = getNumberOfPages();
			
			var rootElement = Y.one(statusElementId);
			rootElement.setContent("");
			if (pages > 0) {
				var from = 1 + (state.page - 1) * state.pageSize;
				if (from < 1) {
				    from = 1;
				}
				var to = state.page * state.pageSize;
				if (to > state.items) {
					to = state.items;
				}
				var of = state.items;
				rootElement.setContent(state.showingResults + from + state.showingResultsTo + to + state.showingResultsOf + of);
				
			}
		},
		
		clear: function() {
			if (state.pagerElements != undefined) {
				for (var i in state.pagerElements) {
					Y.one(state.pagerElements[i]).setContent("");
				}
			}
			if (state.statusElements != undefined) {
				for (var i in state.statusElements) {
					Y.one(state.statusElements[i]).setContent("");
				}
			}
		}
	});
	
	Y.Pager = Pager;
}, '0.0.1', { requires:['node']});