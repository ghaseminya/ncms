/**
 * Hierachical folder-like navigation tree support mixin.
 *
 * @asset(qx/icon/${qx.icontheme}/22/places/folder.png)
 * @asset(qx/icon/${qx.icontheme}/22/places/folder-open.png)
 * @asset(qx/icon/${qx.icontheme}/22/mimetypes/office-document.png)
 * @asset(ncms/icon/22/state/loading.gif)
 */
qx.Mixin.define("ncms.cc.tree.MFolderTree", {

    events : {

        /**
         * DATA: var item = {
         *        "id"     : {Object} Optional Node ID
         *        "label"  : {String} Item name.
         *        "status" : {Number} (statis & 1) != 0 - it is folder, == 0 - otherwise
         *        "path"   : {String} Path to the item (from tree root)
         *       };
         * or null if selection cleared
         */
        itemSelected : "qx.event.type.Data"
    },

    members : {

        _treeConfig : null,

        _tree : null,


        /**
         *
         * cfg:
         * {
         *   action : {String} Action name (defined in ncms.Actions) used to load childen (required),
         *
         *   delegate : {Object?null} VirtualTree delegate used to customize tree.
         *
         *   rootLabel : {String?'root'},
         *
         *   idPathSegments : {Boolean?false} If true Node IDs will be used in path segments
         *
         *   selectRootAsNull : {Boolean} If true, root selection will be fired as `null`
         * }
         */
        _initTree : function(cfg) {
            cfg = this._treeConfig = cfg || {};
            var me = this;
            var root = qx.data.marshal.Json.createModel({
                "id" : null, //generic node ID
                "label" : (cfg["rootLabel"] || this.tr("Root")),   // node name
                "status" : 1,       // (statis & 1) != 0 - it is folder, == 0 - otherwise
                "icon" : "default", // icon alias
                "loaded" : true,    // is loaded
                "children" : []     // node children
            }, true);

            this._loadChildren(root, function() {
                var tree = this._tree = new sm.ui.tree.ExtendedVirtualTree(root, "label", "children");
                tree.setHideRoot(false);
                tree.setIconPath("icon");
                tree.setIconOptions({
                    converter : function(value, model) {
                        switch (value) {
                            case "default":
                                if (model.getChildren != null) {
                                    return "icon/22/places/folder.png";
                                } else {
                                    return "icon/22/mimetypes/office-document.png";
                                }
                                break;
                            default:
                                return "ncms/icon/22/state/loading.gif";
                        }
                    }
                });

                var delegate = {

                    createItem : function() {
                        return new sm.ui.tree.ExtendedVirtualTreeItem();
                    },

                    configureItem : function(item) {
                        item.setOpenSymbolMode("always");
                        item.setIconOpened("icon/22/places/folder-open.png");
                    },

                    bindItem : function(controller, item, index) {
                        controller.bindDefaultProperties(item, index);
                        controller.bindProperty("", "open", {
                            converter : function(value, model, source, target) {
                                var open = target.isOpen();
                                if (open && !value.getLoaded()) {
                                    me._loadChildren(value, function() {
                                        value.setLoaded(true);
                                    });
                                }
                                return open;
                            }
                        }, item, index);
                    }
                };

                if (cfg["delegate"] != null) { //apply delegate properties from config
                    qx.lang.Object.mergeWith(delegate, cfg["delegate"], true);
                }

                tree.setDelegate(delegate);
                tree.getSelection().addListener("change", function(e) {
                    this._onSelected(e.getTarget().getItem(0));
                }, this);

                this.addTree(tree);
            }, this);
        },

        addTree : function(tree) {
            this._add(tree);
        },

        _getItemPathSegments : function(item) {
            if (this._tree == null ||
                    item == null ||
                    item == this._tree.getModel()) {
                return [];
            }
            var cfg = this._treeConfig;
            var root = this._tree.getModel();
            var path = (cfg["idPathSegments"] === true) ? [item.getId()] : [item.getLabel()];
            var pp = this._tree.getParent(item);
            while (pp != null && pp != root) {
                path.push((cfg["idPathSegments"] === true) ? pp.getId() : pp.getLabel());
                pp = this._tree.getParent(pp);
            }
            path.reverse();
            return path;
        },

        _getItemParentId : function(item) {
            if (item == null) {
                return null;
            }
            var pp = this._tree.getParent(item);
            return pp != null ? pp.getId() : null;

        },

        _getItemParentLabel : function(item) {
            if (item == null) {
                return null;
            }
            var pp = this._tree.getParent(item);
            return pp != null ? pp.getId() : null;
        },

        _refreshNode : function(node, cb, self) {
            if (this._tree.isNode(node)) {
                this._loadChildren(node, function() {
                    this._tree.openNodeAndParents(node);
                    if (cb != null) {
                        cb.call(self);
                    }
                }, this);
            } else {
                if (cb) {
                    cb.call(self);
                }
            }
        },

        _onSelected : function(item) {
            var cfg = this._treeConfig;
            if (cfg["selectRootAsNull"]) {

            }
            var data = (item == null || (cfg["selectRootAsNull"] && item == this._tree.getModel())) ? null : {
                "id" : item.getId(),
                "label" : item.getLabel(),
                "status" : item.getStatus(),
                "path" : this._getItemPathSegments(item)

            };
            if (this.hasListener("itemSelected")) {
                this.fireDataEvent("itemSelected", data);
            }
        },

        _loadChildren : function(parent, cb, self) {
            var cfg = this._treeConfig;
            var url = ncms.Application.ACT.getRestUrl(cfg["action"],
                    this._getItemPathSegments(parent));
            var req = new sm.io.Request(url, "GET", "application/json");
            req.send(function(resp) {
                var data = resp.getContent();
                var children = parent.getChildren();
                children.removeAll();
                for (var i = 0, l = data.length; i < l; ++i) {
                    var node = data[i];
                    if (node["id"] === undefined) {
                        node["id"] = null;
                    }
                    node["icon"] = "default";
                    if ((node["status"] & 1) !== 0) {
                        node["loaded"] = false;
                        node["children"] = [
                            {
                                label : "Loading",
                                icon : "loading"
                            }
                        ]
                    } else {
                        node["loaded"] = true;
                    }
                    children.push(qx.data.marshal.Json.createModel(node, true));
                }
                if (cb != null) {
                    cb.call(self);
                }
            }, this);
        }
    },

    destruct : function() {
        this._tree = null;
        //this._disposeObjects("__field_name");                                
    }
});