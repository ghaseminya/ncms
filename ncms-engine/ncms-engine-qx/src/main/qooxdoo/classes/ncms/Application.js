/**
 * Ncms Application
 *
 * WSA abbrev means: workspace area (big right side zone)
 *
 *
 * @use(ncms.asm.AsmNav)
 * @use(ncms.mmgr.MediaNav)
 * @use(ncms.pgs.PagesNav)
 * @use(ncms.usr.UsersNav)
 *
 * @asset(ncms/icon/16/help/help.png)
 * @asset(ncms/icon/16/misc/door_in.png)
 *
 * @asset(qx/icon/${qx.icontheme}/48/status/dialog-information.png)
 */
qx.Class.define("ncms.Application", {
    extend : qx.application.Standalone,
    include : [qx.locale.MTranslation],
    statics : {

        INSTANCE : null,
        APP_STATE : null,
        ACT : null,

        INFO_POPUP : null,

        ///////////////////////////////////////////////////////////
        //                         Alerts
        ///////////////////////////////////////////////////////////

        confirm : function(message, callback, context) {
            (new sm.dialog.Confirm({
                "message" : message,
                "callback" : callback || null,
                "context" : context || null
            })).open();
        },

        alert : function(message, callback, context) {
            (new sm.dialog.Message({
                "message" : message,
                "callback" : callback || null,
                "context" : context || null
            })).open();
        },

        warning : function(message, callback, context) {
            (new sm.dialog.Message({
                "message" : message,
                "callback" : callback || null,
                "context" : context || null,
                "image" : "icon/48/status/dialog-warning.png"
            })).open();
        },

        /**
         * Show autohided popup message
         *
         * @param message {String} message to show
         * @param showTime {Number|null} time in ms to show message, defaul 1500 ms
         * @param hideTime {Number|null} fade out animation duration, default 500 ms
         * @param options {Object|null} additional options:
         *       - icon - icon to show. by default of if <code>undefined</code> will be shown default icon: status/dialog-information,
         *                              if <code>null</code> no icon will be shown
         */
        infoPopup : function(message, showTime, hideTime, options) {
            var root = qx.core.Init.getApplication().getRoot();
            var info = ncms.Application.INFO_POPUP;
            if (!info) {
                info = ncms.Application.INFO_POPUP = new qx.ui.container.Composite(new qx.ui.layout.VBox(0).set({alignX: "center"}))
                info.addListener("resize", function() {
                    var parent = this.getLayoutParent();
                    if (parent) {
                      var bounds = parent.getBounds();
                      if (bounds) {
                        var hint = this.getSizeHint();
                          var left = Math.round((bounds.width - hint.width) / 2);
                          this.setLayoutProperties({
                              left : left,
                              top : 0
                          });
                      }
                    }
                }, info);

                root.add(info);
            } else {
                var maxWindowZIndex = info.getZIndex();
                var windows = root.getWindows();
                for (var i = 0; i < windows.length; i++) {
                    if (windows[i] != this) {
                        var zIndex = windows[i].getZIndex();
                        maxWindowZIndex = Math.max(maxWindowZIndex, zIndex);
                    }
                }
                info.setZIndex(maxWindowZIndex + 1e8);
            }

            options = options || {};
            options["icon"] = options["icon"] !== undefined ? options["icon"] : "icon/48/status/dialog-information.png";

            var el = new qx.ui.basic.Atom(message, options["icon"]).set({
                center: true,
                rich : true,
                selectable : true,
                appearance : "ncms-info-popup"
            });
            info.add(el);

            var fadeOut = {
                duration : hideTime || 500,
                delay : showTime || 1500,
                timing : "ease-out",
                keep : 100,
                keyFrames : {
                    0 : {opacity : 1},
                    100 : {opacity : 0, display : "none"}
                }
            };

            el.addListenerOnce("appear", function() {
                var ah = qx.bom.element.Animation.animate(this.getContentElement().getDomElement(), fadeOut);
                ah.once("end", function() {
                    this.destroy();
                }, this);
                this.addListener("click", function() {
                    ah.stop();
                    this.destroy();
                }, this);
            }, el);
        },

        getComponent : function(name) {
            return ncms.Application.INSTANCE.getComponent(name);
        },

        registerComponent : function(name, component) {
            return ncms.Application.INSTANCE.registerComponent(name, component);
        },

        getUserId : function() {
            return ncms.Application.APP_STATE.getUserId();
        },

        userHasRole : function(role) {
            return ncms.Application.APP_STATE.userHasRole(role);
        },

        userInRoles : function(roles) {
            return ncms.Application.APP_STATE.userInRoles(roles);
        },

        activateWorkspace : function(wsSpec) {
            return ncms.Application.INSTANCE.activateWorkspace(wsSpec);
        },

        registerWorkspaces : function(wsSpec) {
            return ncms.Application.INSTANCE.registerWorkspaces(wsSpec);
        },

        getActiveWorkspace : function() {
            return ncms.Application.INSTANCE.getActiveWorkspace();
        },

        formatDate : function(date, formatName) {
            if (typeof date === "number") {
                date = new Date(date);
            } else if (typeof date === "string") {
                date = new Date(date);
            }
            var format = qx.locale.Date.getDateFormat(formatName || "medium").toString();
            if (format == null) {
                format = qx.locale.Date.getDateFormat("medium").toString();
            }
            var df = new qx.util.format.DateFormat(format);
            return df.format(date);
        }
    },


    events : {

        /**
         * Fired when main gui widget created and attached
         */
        "guiInitialized" : "qx.event.type.Event",


        /**
         * Fired if specifig admin GUI workspace activated
         * Data: {"qxClass":<workspace loader class>, "label":<workspace name>}
         * Example data: {"qxClass":"ncms.pgs.PagesNav","label":"Страницы"}
         */
        "workspaceActivated" : "qx.event.type.Data"


    },


    members : {

        /**
         * Refs to gui components
         */
        __components : null,

        __header : null,

        __nav : null,

        main : function() {

            //load AsmAttrManagersRegistry
            ncms.asm.am.AsmAttrManagersRegistry


            // Enable logging in debug variant
            if (qx.core.Environment.get("ncms.debug")) {
                qx.log.appender.Native;
                qx.log.appender.Console;
            }
            // Call super class
            this.base(arguments);
            this.__components = {};
            this.__bootstrap();

            this.getRoot().setBlockerColor("black");
            this.getRoot().setBlockerOpacity(0.5);

            var comp = new qx.ui.container.Composite(new qx.ui.layout.Dock().set({separatorY : "separator-vertical"}));
            this.getRoot().add(comp, {edge : 0});

            //Toolbar
            var toolbar = new ncms.Toolbar();
            comp.add(toolbar, {edge : "north"});

            var hsp = new qx.ui.splitpane.Pane();
            comp.add(hsp);

            //Left nav side
            var navStack = new sm.ui.cont.LazyStack();
            navStack.setWidth(250);
            hsp.add(navStack, 0);

            //Right nav side
            var rightStack = this.__createRightStack();
            hsp.add(rightStack, 1);

            this.registerComponent("toolbar", toolbar);
            this.registerComponent("nav-stack", navStack);
            this.registerComponent("right-stack", rightStack);

            this.showDefaultWSA();
            this.fireEvent("guiInitialized");
        },

        getComponent : function(name) {
            var val = this.__components[name];
            if (!val) {
                throw new Error("Unknown component: '" + name + "'");
            }
            return val;
        },

        registerComponent : function(name, component) {
            var val = this.__components[name];
            if (val) {
                throw new Error("Component with name: " + name + " already registered");
            }
            this.__components[name] = component;
        },

        activateWorkspace : function(wsSpec) {
            qx.log.Logger.info("Activate workspace: " + JSON.stringify(wsSpec));
            this.getComponent("nav-stack").showWidget(wsSpec["qxClass"]);
            this.fireDataEvent("workspaceActivated", wsSpec);
        },

        registerWorkspaces : function(wsList) {
            //it is LazyStack
            var ns = this.getComponent("nav-stack");
            for (var i = 0, l = wsList.length; i < l; ++i) {
                var wspec = wsList[i];
                var cname = wspec["qxClass"];
                qx.log.Logger.info("Registering workspace: " + cname);
                if (cname == null) {
                    continue;
                }
                ns.registerWidget(cname, function(id, opts) {
                    var clazz = qx.Class.getByName(id);
                    var wspec = opts["wspec"];
                    qx.log.Logger.info("Creating new instance of workspace: " + clazz);
                    if (!clazz) {
                        throw new Error("Class: '" + id + "' is not defined");
                    }
                    var cargs = Array.isArray(wspec["args"]) ? wspec["args"] : undefined;
                    return this.__construct(clazz, cargs);
                }, {cache : true, wspec : wspec}, this);
            }
        },

        getActiveWorkspace : function() {
            return this.getComponent("nav-stack").getActiveWidget();
        },


        /**
         * Display default workspace area placeholder
         * widget (ncms.wsa.WSAPlaceholder)
         */
        showDefaultWSA : function() {
            return this.showWSA("ncms.wsa.WSAPlaceholder");
        },

        /**
         * WSA abbrev means: workspace area (big right side zone)
         * @param widgetId {String}
         */
        showWSA : function(widgetId) {
            return this.getComponent("right-stack").showWidget(widgetId);
        },

        /**
         * WSA abbrev means: workspace area (big right side zone)
         * @param widgetId {String}
         * @returns {Widget|null|*}
         */
        getWSA : function(widgetId) {
            return this.getComponent("right-stack").getWidget(widgetId, true);
        },

        /**
         * Return active WSA ID
         * @returns {String|null}
         */
        getActiveWSAID : function() {
            return this.getComponent("right-stack").getActiveWidgetId();
        },

        /**
         * WSA abbrev means: workspace area (big right side zone)
         * @see sm.ui.cont.LazyStack.registerWidget
         */
        registerWSA : function(widgetId, factory, opts, self) {
            this.getComponent("right-stack").registerWidget(widgetId, factory, opts, self);
        },

        __createRightStack : function() {
            var rs = new sm.ui.cont.LazyStack();
            rs.setWidgetsHidePolicy("exclude");
            rs.registerWidget("ncms.wsa.WSAPlaceholder", function() {
                return new ncms.wsa.WSAPlaceholder()
            }, null, this);
            return rs;
        },

        __bootstrap : function() {
            ncms.Application.INSTANCE = this;
            ncms.Application.APP_STATE = new ncms.AppState("app.state");
        },

        __construct : sm.lang.Object.newInstance
    },

    defer : function(statics) {
        //Class modulations
        qx.Class.include(qx.ui.table.Table, qx.ui.table.MTableContextMenu);
        if (statics.ACT == null) {
            statics.ACT = new ncms.Actions();
        }
    }
});