/**
 * Wrapper for {@link MainPageAM} options form
 */
qx.Class.define("ncms.asm.am.MainPageAMOptionsWidget", {
    extend: sm.ui.form.FlexFormRenderer,
    include: [qx.locale.MTranslation, ncms.asm.am.MAttributeManager],

    events: {
        /** Fired when data in any field in form changes */
        "changeValue": "qx.event.type.Data"
    },

    construct: function (attrSpec) {
        var form = this.__form = new qx.ui.form.Form();
        var opts = ncms.Utils.parseOptions(attrSpec["options"]);

        var el = new qx.ui.form.TextField();
        if (opts["lang"] != null) {
            el.setValue(opts["lang"]);
        }
        el.setPlaceholder(this.tr("Two letter language codes separated by comma"));
        el.addListener("input", this.__onChange, this);
        form.add(el, this.tr("Language codes"), null, "lang");

        el = new qx.ui.form.TextField();
        if (opts["vhost"] != null) {
            el.setValue(opts["vhost"]);
        }
        el.setPlaceholder(this.tr("Comma separated virtual hosts"));
        el.addListener("input", this.__onChange, this);
        form.add(el, this.tr("Virtual hosts"), null, "vhost");

        el = new qx.ui.form.TextArea();
        this._fetchAttributeValue(attrSpec, function (val) {
            if (sm.lang.String.isEmpty(val)) {
                return;
            }
            var value = JSON.parse(val)['robots.txt'];
            if (value != null) {
                el.setValue(value);
            }
        });
        el.setMaxLength(10000);
        el.setPlaceholder(this.tr("max length 10000 chars"));
        el.addListener("input", this.__onChange, this);
        form.add(el, "robots.txt", null, "robots.txt");

        el = new qx.ui.form.CheckBox();
        if (opts["enabled"] == "true") {
            el.setValue(true);
        }
        el.addListener("click", this.__onChange, this);
        form.add(el, this.tr("Enabled"), null, "enabled");

        this.base(arguments, form);
    },

    members: {

        __form: null,

        __onChange: function () {
            this.fireEvent("changeValue");
        },

        _optionsAsJSON: function () {
            if (this.__form == null || !this.__form.validate()) {
                return null;
            }
            var items = this.__form.getItems();
            return {
                options: {
                    "lang": items["lang"] != null ? items["lang"].getValue() : null,
                    "vhost": items["vhost"] != null ? items["vhost"].getValue() : null,
                    "enabled": items["enabled"] != null ? items["enabled"].getValue() : null
                },
                value: {
                    "robots.txt": items["robots.txt"] != null ? items["robots.txt"].getValue() : null
                }
            };
        }
    },

    destruct: function () {
        this._disposeObjects("__form");
    }
});