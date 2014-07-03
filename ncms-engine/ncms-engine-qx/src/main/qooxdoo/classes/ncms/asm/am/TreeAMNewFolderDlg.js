/**
 * New folder dialog
 */

qx.Class.define("ncms.asm.am.TreeAMNewFolderDlg", {
    extend : sm.ui.form.BaseSavePopupDlg,

    construct : function() {
        this.base(arguments);
    },

    members : {

        _configureForm : function() {
            var el = new qx.ui.form.TextField().set({allowGrowY : true, maxLength : 32, required : true});
            el.addListener("keypress", function(ev) {
                if (ev.getKeyIdentifier() == "Enter") {
                    this.save();
                }
            }, this);
            el.setToolTipText(this.tr("Folder name"));
            this._form.add(el, this.tr("Name"), null, "name");
            el.focus();
        },

        _save : function() {
            var items = this._form.getItems();
            var data = items["name"].getValue();
            this.fireDataEvent("completed", data);
        }
    },

    destruct : function() {
        //this._disposeObjects("__field_name");                                
    }
});