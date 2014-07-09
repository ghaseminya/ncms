/**
 * ImageAM value widget
 *
 * @asset(ncms/icon/16/misc/image.png)
 */
qx.Class.define("ncms.asm.am.ImageAMValueWidget", {
    extend : qx.ui.core.Widget,
    implement : [ qx.ui.form.IForm,
                  qx.ui.form.IStringForm,
                  ncms.asm.am.IValueWidget],
    include : [ ncms.asm.am.MValueWidget,
                sm.ui.form.MStringForm ],

    construct : function(attrSpec, asmSpec) {
        this.base(arguments);
        this._setLayout(new qx.ui.layout.VBox(5));
        this.addState("widgetNotReady");

        this.__asmSpec = asmSpec;
        this.__attrSpec = attrSpec;
        this.__options = ncms.Utils.parseOptions(attrSpec["options"]);

        //Button field
        var bf = this.__bf = new sm.ui.form.ButtonField(this.tr("Select image"),
                "ncms/icon/16/misc/image.png", true);
        bf.setReadOnly(true);
        bf.addListener("execute", this.__onExecuteBf, this);
        bf.addListener("changeValue", function() {
            this.fireEvent("modified");
        }, this);
        this._add(bf);

        //Labels
        this.__labels = [new qx.ui.basic.Label(), new qx.ui.basic.Label()];
        this.__labels.forEach(function(l) {
            this._add(l);
            l.exclude();
        }, this);

        //Image
        var image = this.__image = new qx.ui.basic.Image();
        this._add(image);
        image.addListener("pointerover", function() {
            image.setCursor("pointer");
        });
        image.addListener("pointerout", function() {
            image.resetCursor();
        });
        image.addListener("tap", function() {
            var udata = this.__value;
            if (udata == null || udata["id"] == null) {
                return;
            }
            var url = ncms.Application.ACT.getRestUrl("media.fileid", udata);
            qx.bom.Window.open(url + "?inline=true");
        }, this);

        this.bind("valid", this.__bf, "valid");

        this.setUserData("ncms.asm.validator", this.__validate);
    },

    members : {

        __bf : null,

        __labels : null,

        __image : null,

        __attrSpec : null,

        __asmSpec : null,

        __options : null,

        __meta : null,

        __value : null,

        /*_forwardStates : {
         invalid : true
         },
         */
        __validate : function() {
            if (this.getRequired() && sm.lang.String.isEmpty(this.__bf.getValue())) {
                this.setValid(false);
                this.setInvalidMessage(this.tr("This field is required"));
                return false;
            }
            var opts = this.__options;
            var meta = this.__meta;

            if (meta && opts && opts["restrict"] == "true") {
                var optsW = Number(opts["width"]);
                var optsH = Number(opts["height"]);
                var metaW = Number(meta["width"]);
                var metaH = Number(meta["height"]);
                var valid = true;

                if (opts["skipSmall"] == "true") { //skip images with smaller dims
                    if ((optsW > 0 && metaW > optsW) || (optsH > 0 && metaH > optsH)) {
                        valid = false;
                    }
                } else {
                    if ((optsW > 0 && metaW != optsW) || (optsH > 0 && metaH != optsH)) {
                        valid = false;
                    }
                }
                if (valid === false) {
                    var restriction = "";
                    restriction += sm.lang.String.isEmpty(opts["width"]) ? "*" : opts["width"];
                    restriction += " x ";
                    restriction += sm.lang.String.isEmpty(opts["height"]) ? "*" : opts["height"];
                    this.setValid(false);
                    this.setInvalidMessage(this.tr("Image size does not match restriction") + ": " + restriction);
                    return false;
                }
            }
            this.setValid(true);
            return true;
        },

        __onExecuteBf : function(ev) {
            var attrSpec = this.__attrSpec;
            var asmSpec = this.__asmSpec;
            var dlg = new ncms.mmgr.PageFilesSelectorDlg(
                    asmSpec["id"],
                    this.tr("Select image file"), {
                        allowModify : true,
                        noLinkText : true
                    });
            dlg.setCtypeAcceptor(ncms.Utils.isImageContentType);
            dlg.open();
            dlg.addListener("completed", function(ev) {
                var data = ev.getData();
                this.__options = ncms.Utils.parseOptions(attrSpec["options"]);
                var udata = {
                    id : data["id"],
                    options : this.__options
                };

                this.__value = udata;
                this.__bf.setValue(data["folder"] + data["name"]);
                this.__image.setSource(ncms.Application.ACT.getRestUrl("media.thumbnail2", udata));

                dlg.close();

                var req = new sm.io.Request(ncms.Application.ACT.getRestUrl("media.meta", udata),
                        "GET", "application/json");
                req.setShowMessages(false);
                req.send(function(resp) {
                    this.__setImageInfo(resp.getContent());
                }, this);
            }, this);
        },

        __setImageInfo : function(info) {
            qx.core.Assert.assertTrue(info != null);

            this.__bf.setValue(info["folder"] + info["name"]);
            var meta = this.__meta = ncms.Utils.parseOptions(info["meta"]);
            var infoLabel = this.__labels[0];
            var msgLabel = this.__labels[1];
            var opts = this.__options;

            if (meta["width"] && meta["height"]) {
                infoLabel.setValue(this.tr("Original image size") +
                        ": " + meta["width"] + " x " + meta["height"]);
                infoLabel.show();
            } else {
                infoLabel.resetValue();
                infoLabel.exclude();
            }
            if (opts["resize"] == "true") {
                var msg = this.tr("Automatic image resize") + ": ";
                msg += sm.lang.String.isEmpty(opts["width"]) ? "*" : opts["width"];
                msg += " x ";
                msg += sm.lang.String.isEmpty(opts["height"]) ? "*" : opts["height"];
                msgLabel.setValue(msg);
                msgLabel.show();
            } else if (opts["restrict"] == "true") {
                var msg = this.tr("Image size required") + ": ";
                var restriction = "";
                restriction += sm.lang.String.isEmpty(opts["width"]) ? "*" : opts["width"];
                restriction += " x ";
                restriction += sm.lang.String.isEmpty(opts["height"]) ? "*" : opts["height"];
                msg += restriction;
                msgLabel.setValue(msg);
                msgLabel.show();
            } else {
                msgLabel.resetValue();
                msgLabel.exclude();
            }

            this.__validate();
        },

        setAttributeValue : function(val) {
            if (sm.lang.String.isEmpty(val)) {
                this.removeState("widgetNotReady");
                this.__bf.resetValue();
                this.__image.resetSource();
                return;
            }
            val = JSON.parse(val);
            if (val["options"] != null) {
                this.__options = val["options"] || {};
            }
            this.__value = val;
            var req = new sm.io.Request(ncms.Application.ACT.getRestUrl("media.meta", val),
                    "GET", "application/json");
            req.setShowMessages(false);
            req.send(function(resp) {
                this.__setImageInfo(resp.getContent());
            }, this);
            req.addListener("finished", function() {
                this.removeState("widgetNotReady");
            }, this);
            this.__image.setSource(ncms.Application.ACT.getRestUrl("media.thumbnail2", val));
        },

        getWidgetValue : function() {
            return this.__value;
        }
    },

    destruct : function() {
        this.__options = null;
        this.__bf = null;
        this.__labels = null;
        this.__image = null;
        this.__attrSpec = null;
        this.__asmSpec = null;
        this.__value = null;
        this.__meta = null;
        //this._disposeObjects("__field_name");
    }
});