jQuery(function() {
    initNav();
    initTree();
    initSlideShow();
    initAccordion();
    initAboutSections();
    initNavSearch();
});

/*
 * Drop in replace functions for setTimeout() & setInterval() that
 * make use of requestAnimationFrame() for performance where available
 * http://www.joelambert.co.uk
 *
 * Copyright 2011, Joe Lambert.
 * Free to use under the MIT license.
 * http://www.opensource.org/licenses/mit-license.php
 */

// requestAnimationFrame() shim by Paul Irish
// http://paulirish.com/2011/requestanimationframe-for-smart-animating/
window.requestAnimFrame = (function() {
    return window.requestAnimationFrame ||
            window.webkitRequestAnimationFrame ||
            window.mozRequestAnimationFrame ||
            window.oRequestAnimationFrame ||
            window.msRequestAnimationFrame ||
            function(/* function */ callback, /* DOMElement */ element) {
                window.setTimeout(callback, 1000 / 60);
            };
})();

/**
 * Behaves the same as setTimeout except uses requestAnimationFrame() where possible for better performance
 * @param {function} fn The callback function
 * @param {int} delay The delay in milliseconds
 */
window.requestTimeout = function(fn, delay) {
    if (!window.requestAnimationFrame && !window.webkitRequestAnimationFrame && !window.mozRequestAnimationFrame && !window.oRequestAnimationFrame && !window.msRequestAnimationFrame) {
        return window.setTimeout(fn, delay);
    }
    var start = new Date().getTime();
    var handle = {};

    function loop() {
        var current = new Date().getTime(),
                delta = current - start;
        delta >= delay ? fn.call() : handle.value = requestAnimFrame(loop);
    }

    handle.value = requestAnimFrame(loop);
    return handle;
};

/**
 * Behaves the same as clearInterval except uses cancelRequestAnimationFrame() where possible for better performance
 * @param {int|object} fn The callback function
 */
window.clearRequestTimeout = function(handle) {
    window.cancelAnimationFrame ? window.cancelAnimationFrame(handle.value) :
    window.webkitCancelRequestAnimationFrame ? window.webkitCancelRequestAnimationFrame(handle.value) :
    window.mozCancelRequestAnimationFrame ? window.mozCancelRequestAnimationFrame(handle.value) :
    window.oCancelRequestAnimationFrame ? window.oCancelRequestAnimationFrame(handle.value) :
    window.msCancelRequestAnimationFrame ? msCancelRequestAnimationFrame(handle.value) :
    clearTimeout(handle);
};


function initTree() {
    $('ul.dynamic li.node > span').click(function() {
        var li = $(this).parent();
        var flag = $(li).hasClass('open');
        if (flag == true) {
            $(li).removeClass('open');
            $(li).addClass('close');
        } else {
            $(li).removeClass('close');
            $(li).addClass('open');
        }
    });
}

function initAboutSections() {
    var about = $('a.about');
    about.click(function(event) {
        var _this = $(this);
        _this.slideUp(300);
        _this.prev("div").slideDown(300);
        event.preventDefault();
    });
    if ($('div#main.faculty-page ul.news-list li').length == 0) {
        about.hide();
        about.prev("div").show();
    }
}

function initNav() {
    jQuery('.slide-frame').sameHeight({
        elements : 'ul',
        flexible : true,
        multiLine : true
    });
    var _nav = $('#nav');
    _nav.find('> ul > li > a').hover(function() {
        var _this = $(this);
        var _lnav = _this.closest('#nav, #nav2');
        if (_lnav.find('> ul > li.active').length == 0) {
            return;
        }
        if (!_this.parent().hasClass("active")) {
            _lnav.find('> ul > li.active').removeClass('active');
            _this.parent().addClass('active');
            _this.closest('.nav-holder').addClass('active');
        }
    }, function() {

    });
    _nav.find('> ul > li > a').click(function(event) {
        var _this = $(this);
        var _lnav = _this.closest('#nav, #nav2');
        if (_this.parent().find('.slide-holder').size()) {
            if (!_this.parent().hasClass('active')) {
                _lnav.find('> ul > li.active').removeClass('active');
                _this.parent().addClass('active');
                _this.closest('.nav-holder').addClass('active');
            } else {
                _this.parent().removeClass('active');
                _this.closest('.nav-holder').removeClass('active');
            }
            event.preventDefault();
        }
    });
    $(document).click(function(event) {
        if ($(event.target).closest(".nav-holder").length) {
            return;
        }
        $('.nav-holder').removeClass('active');
        $('#nav').find('> ul > li.active').removeClass('active');
        event.stopPropagation();
    });

    $('ul.nav2 > li > ul').hover(function() {
        $(this).closest('li').find('> a').addClass('active');
    }, function() {
        $(this).closest('li').find('> a').removeClass('active');
    });
}


function initSlideShow() {
    $('.gallery-holder, .slider-holder').fadeGallery({
        slideElements : '.slide',
        pauseOnHover : true,
        autoRotation : true,
        switchTime : 10000,
        duration : 650,
        event : 'click'
    });
    $('.photo-line').tinycarousel({
        interval : true
    });
}

jQuery.fn.fadeGallery = function(_options) {
    var _options = jQuery.extend({
        slideElements : 'div.slideset > div',
        pagerLinks : '.control-panel li',
        btnNext : 'a.btn-next',
        btnPrev : 'a.btn-prev',
        btnPlayPause : 'a.play-pause',
        pausedClass : 'paused',
        playClass : 'playing',
        activeClass : 'active',
        pauseOnHover : true,
        autoRotation : false,
        autoHeight : false,
        switchTime : 3000,
        duration : 650,
        event : 'click'
    }, _options);

    return this.each(function() {
        var _this = jQuery(this);
        var _slides = jQuery(_options.slideElements, _this);
        var _pagerLinks = jQuery(_options.pagerLinks, _this);
        var _btnPrev = jQuery(_options.btnPrev, _this);
        var _btnNext = jQuery(_options.btnNext, _this);
        var _btnPlayPause = jQuery(_options.btnPlayPause, _this);
        var _pauseOnHover = _options.pauseOnHover;
        var _autoRotation = _options.autoRotation;
        var _activeClass = _options.activeClass;
        var _pausedClass = _options.pausedClass;
        var _playClass = _options.playClass;
        var _autoHeight = _options.autoHeight;
        var _duration = _options.duration;
        var _switchTime = _options.switchTime;
        var _controlEvent = _options.event;

        var _hover = false;
        var _prevIndex = 0;
        var _currentIndex = 0;
        var _slideCount = _slides.length;
        var _timer;
        if (!_slideCount) return;
        _slides.hide().eq(_currentIndex).show();
        if (_autoRotation) _this.removeClass(_pausedClass).addClass(_playClass);
        else _this.removeClass(_playClass).addClass(_pausedClass);

        if (_btnPrev.length) {
            _btnPrev.bind(_controlEvent, function() {
                prevSlide();
                return false;
            });
        }
        if (_btnNext.length) {
            _btnNext.bind(_controlEvent, function() {
                nextSlide();
                return false;
            });
        }
        if (_pagerLinks.length) {
            _pagerLinks.each(function(_ind) {
                jQuery(this).bind(_controlEvent, function() {
                    if (_currentIndex != _ind) {
                        _prevIndex = _currentIndex;
                        _currentIndex = _ind;
                        switchSlide();
                    }
                    return false;
                });
            });
        }

        if (_btnPlayPause.length) {
            _btnPlayPause.bind(_controlEvent, function() {
                if (_this.hasClass(_pausedClass)) {
                    _this.removeClass(_pausedClass).addClass(_playClass);
                    _autoRotation = true;
                    autoSlide();
                } else {
                    if (_timer) clearRequestTimeout(_timer);
                    _this.removeClass(_playClass).addClass(_pausedClass);
                }
                return false;
            });
        }

        function prevSlide() {
            _prevIndex = _currentIndex;
            if (_currentIndex > 0) _currentIndex--;
            else _currentIndex = _slideCount - 1;
            switchSlide();
        }

        function nextSlide() {
            _prevIndex = _currentIndex;
            if (_currentIndex < _slideCount - 1) _currentIndex++;
            else _currentIndex = 0;
            switchSlide();
        }

        function refreshStatus() {
            if (_pagerLinks.length) _pagerLinks.removeClass(_activeClass).eq(_currentIndex).addClass(_activeClass);
            _slides.eq(_prevIndex).removeClass(_activeClass);
            _slides.eq(_currentIndex).addClass(_activeClass);
        }

        function switchSlide() {
            _slides.stop(true, true);
            _slides.eq(_prevIndex).fadeOut(_duration);
            _slides.eq(_currentIndex).fadeIn(_duration);
            refreshStatus();
            autoSlide();
        }

        function autoSlide() {
            if (!_autoRotation || _hover) return;
            if (_timer) clearRequestTimeout(_timer);
            _timer = requestTimeout(nextSlide, _switchTime + _duration);
        }

        if (_pauseOnHover) {
            _this.hover(function() {
                _hover = true;
                if (_timer) clearRequestTimeout(_timer);
            }, function() {
                _hover = false;
                autoSlide();
            });
        }
        refreshStatus();
        autoSlide();
    });
};

function initAccordion() {
    $('.accordion > li > a').click(function(event) {
        var _this = $(this);
        if (!_this.parent().hasClass('active')) {
            _this.parent().find('ul').slideDown(200, function() {
                _this.parent().addClass('active');
            });
        } else {
            _this.parent().find('ul').slideUp(200, function() {
                _this.parent().removeClass('active');
            });
        }
        event.preventDefault();
    });
    var selfs = $('.side-list > .box').find('li.self');
    if (selfs.length < 1) {
        $('.side-list > .box').first().addClass('active');
    } else {
        $(selfs[0]).closest('li.box').addClass('active');
    }
    $('.side-list > .box > a').click(function(event) {
        if (!$(this).parent().hasClass('active')) {
            $(this).parents('.side-list').children('li.active').find('ul').slideUp(200, function() {
                $(this).parent().removeClass('active');
            });
            $(this).parent().find('ul').slideDown(200, function() {
                $(this).parent().addClass('active')
            });
        }
        event.preventDefault();
    })
}

//Tinycarousel plugin
(function($) {
    var pluginName = "tinycarousel"
            , defaults = {
                start : 0      // The starting slide
                , axis : "x"    // vertical or horizontal scroller? ( x || y ).
                , buttons : false   // show left and right navigation buttons.
                , bullets : false  // is there a page number navigation present?
                , interval : false  // move to another block on intervals.
                , intervalTime : 8000   // interval time in milliseconds.
                , animation : true   // false is instant, true is animate.
                , animationTime : 1000   // how fast must the animation move in ms?
                , infinite : true   // infinite carousel.
            };

    function Plugin($container, options) {
        this.options = $.extend({}, defaults, options);
        this._defaults = defaults;
        this._name = pluginName;

        var self = this
                , $viewport = $container.find(".viewport:first")
                , $overview = $container.find(".overview:first")
                , $slides = 0
                , $next = $container.find(".next:first")
                , $prev = $container.find(".prev:first")
                , $bullets = $container.find(".bullet")

                , viewportSize = 0
                , contentStyle = {}
                , slidesVisible = 0
                , slideSize = 0
                , slideIndex = 0

                , isHorizontal = this.options.axis === 'x'
                , sizeLabel = isHorizontal ? "Width" : "Height"
                , posiLabel = isHorizontal ? "left" : "top"
                , intervalTimer = null;

        this.slideCurrent = 0;
        this.slidesTotal = 0;

        function initialize() {
            self.update();
            self.move(self.slideCurrent);
            setEvents();
            return self;
        }

        this.update = function() {
            $overview.find(".mirrored").remove();

            $slides = $overview.children();
            viewportSize = $viewport[0]["offset" + sizeLabel];
            slideSize = $slides.first()["outer" + sizeLabel](true);
            self.slidesTotal = $slides.length;
            self.slideCurrent = self.options.start || 0;
            slidesVisible = Math.ceil(viewportSize / slideSize);

            $overview.append($slides.slice(0, slidesVisible).clone().addClass("mirrored"));
            $overview.css(sizeLabel.toLowerCase(), slideSize * (self.slidesTotal + slidesVisible));

            return self;
        };

        function setEvents() {
            if (self.options.buttons) {
                $prev.click(function() {
                    self.move(--slideIndex);
                    return false;
                });

                $next.click(function() {
                    self.move(++slideIndex);
                    return false;
                });
            }

            $(window).resize(self.update);

            if (self.options.bullets) {
                $container.on("click", ".bullet", function() {
                    self.move(slideIndex = +$(this).attr("data-slide"));
                    return false;
                });
            }
        }

        this.start = function() {
            if (self.options.interval) {
                clearTimeout(intervalTimer);
                intervalTimer = setTimeout(function() {
                    self.move(++slideIndex);

                }, self.options.intervalTime);
            }
            return self;
        };

        this.stop = function() {
            clearTimeout(intervalTimer);
            return self;
        };

        this.move = function(index) {
            slideIndex = index;
            self.slideCurrent = slideIndex % self.slidesTotal;

            if (slideIndex < 0) {
                self.slideCurrent = slideIndex = self.slidesTotal - 1;
                $overview.css(posiLabel, -(self.slidesTotal) * slideSize);
            }

            if (slideIndex > self.slidesTotal) {
                self.slideCurrent = slideIndex = 1;
                $overview.css(posiLabel, 0);
            }

            contentStyle[posiLabel] = -slideIndex * slideSize;

            $overview.animate(
                    contentStyle, {
                        queue : false, duration : self.options.animation ? self.options.animationTime : 0, always : function() {
                            $container.trigger("move", [$slides[self.slideCurrent], self.slideCurrent]);
                        }
                    });

            setButtons();
            self.start();
            return self;
        };

        function setButtons() {
            if (self.options.buttons && !self.options.infinite) {
                $prev.toggleClass("disable", self.slideCurrent <= 0);
                $next.toggleClass("disable", self.slideCurrent >= self.slidesTotal - slidesVisible);
            }

            if (self.options.bullets) {
                $bullets.removeClass("active");
                $($bullets[self.slideCurrent]).addClass("active");
            }
        }

        return initialize();
    }

    $.fn[pluginName] = function(options) {
        return this.each(function() {
            if (!$.data(this, "plugin_" + pluginName)) {
                $.data(this, "plugin_" + pluginName, new Plugin($(this), options));
            }
        });
    };
})(jQuery);


/*
 * SameHeight plugin
 */
(function($) {
    $.fn.sameHeight = function(opt) {
        var options = $.extend({
            skipClass : 'same-height-ignore',
            leftEdgeClass : 'same-height-left',
            rightEdgeClass : 'same-height-right',
            elements : '>*',
            flexible : false,
            multiLine : false,
            useMinHeight : false,
            biggestHeight : false
        }, opt);
        return this.each(function() {
            var holder = $(this),
                    postResizeTimer, ignoreResize;
            var elements = holder.find(options.elements).not('.' + options.skipClass);
            if (!elements.length) return;

            // resize handler
            function doResize() {
                elements.css(options.useMinHeight && supportMinHeight ? 'minHeight' : 'height', '');
                if (options.multiLine) {
                    // resize elements row by row
                    resizeElementsByRows(elements, options);
                } else {
                    // resize elements by holder
                    resizeElements(elements, holder, options);
                }
            }

            doResize();

            // handle flexible layout / font resize
            var delayedResizeHandler = function() {
                if (!ignoreResize) {
                    ignoreResize = true;
                    doResize();
                    clearTimeout(postResizeTimer);
                    postResizeTimer = setTimeout(function() {
                        doResize();
                        setTimeout(function() {
                            ignoreResize = false;
                        }, 10);
                    }, 100);
                }
            };

            // handle flexible/responsive layout
            if (options.flexible) {
                $(window).bind('resize orientationchange fontresize', delayedResizeHandler);
            }

            // handle complete page load including images and fonts
            $(window).bind('load', delayedResizeHandler);
        });
    };

    // detect css min-height support
    var supportMinHeight = typeof document.documentElement.style.maxHeight !== 'undefined';

    // get elements by rows
    function resizeElementsByRows(boxes, options) {
        var currentRow = $(),
                maxHeight, maxCalcHeight = 0,
                firstOffset = boxes.eq(0).offset().top;
        boxes.each(function(ind) {
            var curItem = $(this);
            if (curItem.offset().top === firstOffset) {
                currentRow = currentRow.add(this);
            } else {
                maxHeight = getMaxHeight(currentRow);
                maxCalcHeight = Math.max(maxCalcHeight, resizeElements(currentRow, maxHeight, options));
                currentRow = curItem;
                firstOffset = curItem.offset().top;
            }
        });
        if (currentRow.length) {
            maxHeight = getMaxHeight(currentRow);
            maxCalcHeight = Math.max(maxCalcHeight, resizeElements(currentRow, maxHeight, options));
        }
        if (options.biggestHeight) {
            boxes.css(options.useMinHeight && supportMinHeight ? 'minHeight' : 'height', maxCalcHeight);
        }
    }

    // calculate max element height
    function getMaxHeight(boxes) {
        var maxHeight = 0;
        boxes.each(function() {
            maxHeight = Math.max(maxHeight, $(this).outerHeight());
        });
        return maxHeight;
    }

    // resize helper function
    function resizeElements(boxes, parent, options) {
        var calcHeight;
        var parentHeight = typeof parent === 'number' ? parent : parent.height();
        boxes.removeClass(options.leftEdgeClass).removeClass(options.rightEdgeClass).each(function(i) {
            var element = $(this);
            var depthDiffHeight = 0;
            var isBorderBox = element.css('boxSizing') === 'border-box';

            if (typeof parent !== 'number') {
                element.parents().each(function() {
                    var tmpParent = $(this);
                    if (parent.is(this)) {
                        return false;
                    } else {
                        depthDiffHeight += tmpParent.outerHeight() - tmpParent.height();
                    }
                });
            }
            calcHeight = parentHeight - depthDiffHeight;
            calcHeight -= isBorderBox ? 0 : element.outerHeight() - element.height();

            if (calcHeight > 0) {
                element.css(options.useMinHeight && supportMinHeight ? 'minHeight' : 'height', calcHeight);
            }
        });
        boxes.filter(':first').addClass(options.leftEdgeClass);
        boxes.filter(':last').addClass(options.rightEdgeClass);
        return calcHeight;
    }
}(jQuery));


function initNavSearch() {
    $('.search-form .search-directions').click(function(ev) {
        var tgt = $(ev.target);
        tgt.closest('div.search-where').find('.search-directions-list').toggle();
        ev.stopPropagation();
    });
    $('.search-form .search-directions-list li').click(function(ev) {
        var tgt = $(ev.target);
        tgt.closest('div.search-directions-list').hide();
        tgt.closest('form')[0].action = tgt.attr('value');
        tgt.closest('div.search-where').find('span.value').html(tgt.html());
        ev.stopPropagation()
    });
}
/*!
 * Scroll Sneak
 * http://mrcoles.com/scroll-sneak/
 *
 * Copyright 2010, Peter Coles
 * Licensed under the MIT licenses.
 * http://mrcoles.com/media/mit-license.txt
 *
 * Date: Mon Mar 8 10:00:00 2010 -0500
 */
var ScrollSneak = function(prefix, wait) {
    // clean up arguments (allows prefix to be optional - a bit of overkill)
    if (typeof(wait) == 'undefined' && prefix === true) prefix = null, wait = true;
    prefix = (typeof(prefix) == 'string' ? prefix : window.location.host).split('_').join('');
    var pre_name;

    // scroll function, if window.name matches, then scroll to that position and clean up window.name
    this.scroll = function() {
        if (window.name.search('^' + prefix + '_(\\d+)_(\\d+)_') == 0) {
            var name = window.name.split('_');
            window.scrollTo(name[1], name[2]);
            window.name = name.slice(3).join('_');
        }
    };
    // if not wait, scroll immediately
    if (!wait) this.scroll();

    this.sneak = function() {
        // prevent multiple clicks from getting stored on window.name
        if (typeof(pre_name) == 'undefined') pre_name = window.name;

        // get the scroll positions
        var top = 0, left = 0;
        if (typeof(window.pageYOffset) == 'number') { // netscape
            top = window.pageYOffset, left = window.pageXOffset;
        } else if (document.body && (document.body.scrollLeft || document.body.scrollTop)) { // dom
            top = document.body.scrollTop, left = document.body.scrollLeft;
        } else if (document.documentElement && (document.documentElement.scrollLeft || document.documentElement.scrollTop)) { // ie6
            top = document.documentElement.scrollTop, left = document.documentElement.scrollLeft;
        }
        // store the scroll
        if (top || left) window.name = prefix + '_' + left + '_' + top + '_' + pre_name;
        return true;
    }
};


$(function init() {
    if ($('input:checkbox').size()) var _checkbox = $('input:checkbox').checkbox();
    if ($('input:radio').size()) var _radio = $('input:radio').radio();
});
$.fn.checkbox = function(o) {
    var callMethod = $.fn.checkbox.method;
    if (typeof o == "string" && o in $.fn.checkbox.method) {
        var checkbox = $(this);
        callMethod[o](checkbox);
        return checkbox;
    }
    if (!("method" in $.fn.checkbox)) {
        $.fn.checkbox.method = {
            "destroy" : function(checkbox) {
                if (checkbox.data('customized')) {
                    checkbox.off('change.customForms');
                    checkbox.each(function() {
                        $(this).data('customCheckbox').off('click.customForms').remove();
                    });
                    checkbox.removeData();
                } else {
                    throw new Error('объект не проинициализирован');
                }
            },
            "check" : function(checkbox) {
                checkbox.trigger('change.customForms', ['check']);
            },
            "uncheck" : function(checkbox) {
                checkbox.trigger('change.customForms', ['uncheck']);
            },
            "toggle" : function(checkbox) {
                var method = this;
                checkbox.each(function() {
                    if (!$(this).is(':checked')) {
                        method.check($(this));
                    } else {
                        method.uncheck($(this));
                    }
                });
            }
        };
        callMethod = $.fn.checkbox.method;
    }
    var checkboxes = [];
    $(this).each(function() {
        if (!$(this).data('customized')) {
            checkboxes.push(this);
        }
    });
    if (!$(this).size()) {
        throw new Error('селектор ' + $(this).selector + ' возвратил пустой набор элементов');
    }
    if (checkboxes.length) {
        o = $.extend({
            "checkboxClass" : "checkboxAreaChecked",
            "labelClass" : "active",
            "customCheckboxClass" : "checkboxArea"
        }, o);
        var customCheckbox = $('<div class="' + o.customCheckboxClass + '"></div>');
        checkboxes = $(checkboxes);
        checkboxes.data('customized', true);
        return checkboxes.each(function() {
            var checkbox = $(this),
                    localCustomCheckbox = customCheckbox.clone(),
                    checkboxClass = o.checkboxClass,
                    labelClass = o.labelClass;
            checkbox.data('customCheckbox', localCustomCheckbox);
            localCustomCheckbox.insertAfter(checkbox);
            if (checkbox.closest('label').size()) {
                checkbox.data('label', checkbox.closest('label'));
            } else if (checkbox.attr('id')) {
                checkbox.data('label', $('label[for=' + checkbox.attr('id') + ']'));
            }
            checkbox.on('change.customForms', function(e, command) {
                if (command == "check") {
                    check();
                } else if (command == "uncheck") {
                    uncheck();
                } else {
                    if (checkbox.is(':checked')) {
                        check();
                    } else {
                        uncheck();
                    }
                }
            }).trigger('change.customForms');
            localCustomCheckbox.on('click.customForms', function(e) {
                if (!localCustomCheckbox.hasClass(checkboxClass)) {
                    callMethod.check(checkbox);
                } else {
                    callMethod.uncheck(checkbox);
                }
                e.preventDefault();
            });

            function check() {
                checkbox.get(0).checked = true;
                localCustomCheckbox.addClass(checkboxClass);
                if (checkbox.data('label')) {
                    checkbox.data('label').addClass(labelClass);
                }
            }

            function uncheck() {
                checkbox.get(0).checked = false;
                localCustomCheckbox.removeClass(checkboxClass);
                if (checkbox.data('label')) {
                    checkbox.data('label').removeClass(labelClass);
                }
            }
        });
    } else {
        throw Error('чекбокс/ы уже проинициализирован/ы');
    }
};

$.fn.radio = function(o) {
    var callMethod = $.fn.radio.method;
    if (typeof o == "string" && o in $.fn.radio.method) {
        var radio = $(this);
        callMethod[o](radio);
        return radio;
    }
    if (!("method" in $.fn.radio)) {
        $.fn.radio.method = {
            "destroy" : function(radio) {
                var initedEls = [];
                radio.each(function() {
                    if ($(this).data('customized')) {
                        initedEls.push(this);
                    }
                });
                if (initedEls.length) {
                    radio = $(initedEls);
                    radio.off('change.customForms');
                    radio.each(function() {
                        $(this).data('customRadio').off('click.customForms').remove();
                    });
                    radio.removeData();
                } else {
                    throw new Error('объект не проинициализирован');
                }
            },
            "check" : function(radio) {
                radio.trigger('change', ['check']);
            }
        };
        callMethod = $.fn.radio.method;
    }
    if (!('group' in $.fn.radio)) {
        $.fn.radio.group = {};
    }
    if (!$(this).size()) {
        throw new Error('селектор ' + $(this).selector + ' возвратил пустой набор элементов');
    }
    var radios = [];
    $(this).each(function() {
        if (!$(this).data('customized')) {
            radios.push(this);
        }
    });
    if (radios.length) {
        o = $.extend({
            "radioClass" : "radioAreaChecked",
            "labelClass" : "active",
            "customRadioClass" : "radioArea"
        }, o);
        var customRadio = $('<div class="' + o.customRadioClass + '"></div>'),
                group = $.fn.radio.group;
        radios = $(radios);
        radios.data('customized', true);
        radios.each(function() {
            if ($(this).attr('name') && !($(this).attr('name') in group))
                group[$(this).attr('name')] = radios.filter('input:radio[name=\'' + $(this).attr('name') + '\']');
        });
        return radios.each(function() {
            var radio = $(this),
                    localCustomRadio = customRadio.clone(),
                    curGroup = radio.attr('name') in group ? group[radio.attr('name')] : 0,
                    radioClass = o.radioClass,
                    labelClass = o.labelClass;
            radio.data('customRadio', localCustomRadio);
            localCustomRadio.insertAfter(radio);
            if (radio.closest('label').size()) {
                radio.data('label', radio.closest('label'));
            } else if (radio.attr('id')) {
                radio.data('label', $('label[for=' + radio.attr('id') + ']'));
            }
            radio.on('change.customForms', function(e, command) {
                if (radio.is(':checked') || command == "check") {
                    if (curGroup) {
                        uncheck(curGroup.not(radio).next());
                        if (curGroup.data('label').size()) {
                            curGroup.each(function() {
                                if ($(this).data('label')) {
                                    $(this).data('label').removeClass('active');
                                }
                            });
                        }
                    }
                    check(localCustomRadio);
                    if (command == "check") check(radio);
                    if (radio.data('label')) {
                        radio.data('label').addClass(labelClass);
                    }
                }
            }).trigger('change.customForms');
            localCustomRadio.on('click.customForms', function(e) {
                if (!localCustomRadio.hasClass(radioClass)) {
                    callMethod.check(radio);
                }
                e.preventDefault();
            });

            function check(radio) {
                if (radio.is('input:radio')) {
                    radio.get(0).checked = true;
                } else {
                    radio.addClass(radioClass);
                }
            }

            function uncheck(radio) {
                if (radio.is('input:radio')) {
                    radio.get(0).checked = false;
                } else {
                    radio.removeClass(radioClass);
                }
            }
        });
    } else {
        throw Error('радиокнопка/и уже проинициализирована/ы');
    }
};

function initSearch(pageSize) {
    var spc = {
        form : $('form#spc-search-form'),
        results : $('ul#spc-search-results'),
        fetchMore : $('a#spc-fetch-more'),
        start : 0,
        pageSize : pageSize || 1
    };

    spc.results.find('li').show();

    function updateSearchButtons() {
        if (spc.results.find('li').size() >= ((spc.start || 0) + spc.pageSize)) {
            spc.fetchMore.finish();
            spc.fetchMore.show();
        }
    }

    function doSearch(reset) {
        if (reset) {
            spc.results.html(null);
        }
        spc.start = reset ? 0 : (spc.start || 0) + spc.pageSize;
        var fdata = spc.form.serializeArray();
        fdata.push({name : "spc.action", value : "search"});
        fdata.push({name : "spc.start", value : spc.start});
        fdata.push({name : "spc.limit", value : spc.pageSize});
        $.post(spc.form[0].action, fdata).done(function(data) {
            spc.results.append(data);
            spc.results.find('li:hidden').slideDown();
            updateSearchButtons();
        });
        spc.fetchMore.slideUp(500);
    }

    spc.form.submit(function() {
        doSearch(true);
        return false;
    });
    spc.fetchMore.click(function() {
        doSearch(false);
        return false;
    });

    spc.fetchMore.hide();
    updateSearchButtons();
}

function initMainNews(aType) {
    var columns = [
        {
            subscribed : true,
            holder : $('div.news-holder'),
            results : $('ul.news-list'),
            type : 'a',
            subType : aType
        },
        {
            subscribed : true,
            holder : $('div.announcement-holder'),
            results : $('ul.announcement-list'),
            type : 'b'
        },
        {
            subscribed : true,
            holder : $('div.faculty-holder'),
            results : $('ul.faculty-list'),
            type : 'c'
        }
    ];

    var k;
    var sheight = 0;
    for (k = 0; k < columns.length; ++k) {
        columns[k].dh = columns[k].holder.height() - columns[k].results.height();
        if (sheight < columns[k].dh) {
            sheight = columns[k].dh;
        }
    }

    for (k = 0; k < columns.length; ++k) {
        columns[k].oh = columns[k].dh - sheight;
    }

    var listHeight = sheight;
    var pageHeight = Math.max(window.innerHeight, 350);
    var main = $('div#news');
    var fetchMoreBtn = $('a#mpc-fetch-more');

    var processing = 0;

    var update = function(clh) {
        if (processing != 0) {
            return;
        }

        var subscribed = false;
        for (var i = 0; i < columns.length; ++i) {
            var column = columns[i];
            subscribed = subscribed || column.subscribed;

            var nh = false;
            var ch = 0;
            column.results.find('li').each(function(i, el) {
                el = $(el);
                ch += el.outerHeight();
                if (nh || (ch > clh - column.dh)) {
                    nh = true;
                    el.hide();
                } else {
                    el.show();
                }
            });

            if (column.holder.height() > listHeight) {
                listHeight = column.holder.height();
            }
        }

        main.height(listHeight);

        if (subscribed) {
            fetchMoreBtn.finish();
            fetchMoreBtn.show();
        }
    };

    var checkHeight = function(def, height) {
        var cheight = 0;
        def.results.find('li').each(function(i, el) {
            cheight += $(el).outerHeight();
        });

        return cheight < height - def.dh;
    };

    var tryLoadData = function(def, needHeight) {
        var data = {
            "mpc.action" : "fetchMore",
            "mpc.fetch.type" : def.type
        };
        var prevSize = data["mpc." + def.type + ".skip"] = def.results.find('li').size();
        if (!!def.subType) {
            data["mpc." + def.type + ".subType"] = def.subType;
        }
        $.post(fetchMoreBtn[0].href, data, function(html) {
            $(html).find('li').hide();
            def.results.append(html);
            if (def.results.find('li').size() == prevSize) {
                def.subscribed = false;
            }
            if (checkHeight(def, needHeight) && def.subscribed) {
                ++processing;
                tryLoadData(def, needHeight);
            }
        }).fail(function() {
            def.subscribed = false;
        }).always(function() {
            --processing;
            update(needHeight);
        });
    };

    var loadPage = function() {
        fetchMoreBtn.slideUp(500);

        var tph = listHeight + pageHeight - sheight;
        for (var i = 0; i < columns.length; ++i) {
            ++processing;
            var column = columns[i];
            if (checkHeight(column, tph) && column.subscribed) {
                tryLoadData(column, tph);
            } else {
                --processing;
            }
        }

        update(tph + sheight);
    };

    fetchMoreBtn.click(function() {
        loadPage();
        return false;
    });

    fetchMoreBtn.hide();
    loadPage();
}

function initFetchMore(btn, action, results, pageSize, prefix) {
    $(btn).hide();
    $(results).find('li').show();
    $(btn).click(function() {
                var data = {};
                data[prefix + ".action"] = "fetchMore";
                data[prefix + ".skip"] = $(results).find('li').size();
                data[prefix + ".limit"] = pageSize;

                $.post(action, data, function(html) {
                    $(btn).hide();
                    var count = $(results).find('li').size();
                    $(results).append(html);
                    if ($(results).find('li').size() - count >= pageSize) {
                        $(btn).show();
                    }
                    $(results).find('li:hidden').slideDown();
                });
                return false;
            }
    );

    if ($(results).find('li').size() >= pageSize) {
        $(btn).show();
    }
}

function initNewsMain(pageSize) {
    var container = $('div#news-container');
    var fetchMore = $('a#news-fetch-more');

    container.imagesLoaded(function() {
        container.masonry({itemSelector : '.news-item'})
    });

    fetchMore.click(function() {
        var count = container.find('.news-item').size();
        fetchMore.hide();
        $.post(null, {
            "mnc.action" : "fetchMoreNews",
            "mnc.news.skip" : count,
            "mnc.news.limit" : pageSize
        }, function(data) {
            var html = $.parseHTML(data);
            container.append(html);
            container.masonry('appended', html);
            if (container.find('.news-item').size() - count >= pageSize) {
                fetchMore.fadeIn(500);
            }
        });
        return false;
    });

    fetchMore.hide();

    if (container.find('.news-item').size() >= pageSize) {
        fetchMore.show();
    }
}

function initRemember() {
    $('.event-list .remember').click(function(event) {
        $('.remember-form').fadeOut(200);
        var form = $(this).closest('li').find('.remember-form');
        var contact = form.find('input[name=contact]');
        contact.val('');
        contact.focus();
        form.fadeIn(200);
        event.preventDefault();
    });
    $('.remember-form .close').click(function(event) {
        var form = $('.remember-form');
        form.fadeOut(200);
        event.preventDefault();
    });
    $('.remember-form').submit(function(event) {
        event.preventDefault();
        var form = $(event.target);
        if ($.trim(form.find('input[name=contact]').val()) == '') {
            return;
        }

        var data = form.serializeArray();
        data.push({name : 'action', value : 'remember'});
        $.post(event.target.action, data, function(resp) {
            var success = resp['success'];
            if (success) {
                form.find('input[type=text]').val('');
                form.parent().find('.remember').hide();
                form.fadeOut(500);
            } else {
                alert(resp['message'] || 'Ошибка сохранения напоминания');
            }
        }).fail(function() {
        });
    });
}

function initScroll(tabSelector, prefix) {
    var sneaky = new ScrollSneak(prefix);
    $(tabSelector).click(function() {
        return sneaky.sneak();
    });
}

function initSiteMap(href) {
    var initLoad = function(el, level) {
        $(el).find('li.node > span').click(function() {
            var li = $(this).parent();
            var ul = li.find('> ul');
            if (ul.html() == '') {
                ul.html('&nbsp;');
                $.post(href, {parent : li.attr('value')}, function(data) {
                    ul.html(data);
                    initLoad(ul, level + 1);
                });
            }
            // first level already initialized
            if (level > 0) {
                var flag = li.hasClass('open');
                if (flag == true) {
                    li.removeClass('open');
                    li.addClass('close');
                } else {
                    li.removeClass('close');
                    li.addClass('open');
                }
            }
        })
    };

    initLoad('#sitemap', 0);
}