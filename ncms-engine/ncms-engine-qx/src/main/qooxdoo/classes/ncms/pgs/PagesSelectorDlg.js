/**
 * Pages selector dialog.
 */
qx.Class.define("ncms.pgs.PagesSelectorDlg", {
    extend : qx.ui.window.Window,

    events : {
        /**
         * Data: {
         *   id : {Number} Page ID,
         *   name : {String} Page name
         * }
         */
        "completed" : "qx.event.type.Data"
    },


    /**
     *
     * @param caption {String?} Dialog caption
     * @param allowModify {Boolean?false} Allow CRUD operations on pages
     * @param options {Map?} Options:
     *                <code>
     *                    {
     *                      foldersOnly : {Boolean?false} //Show only folders,
     *                      allowRootSelection : {Boolen?false}
     *                    }
     *                </code>
     *
     */
    construct : function(caption, allowModify, options) {
        this._options = options || {};
        this.base(arguments, caption != null ? caption : this.tr("Select page"));
        this.setLayout(new qx.ui.layout.VBox(5));
        this.set({
            modal : true,
            showMinimize : false,
            showMaximize : true,
            allowMaximize : true,
            width : 620,
            height : 400
        });

        var selector = this._selector = new ncms.pgs.PagesSelector(!!allowModify, options);
        this.add(selector, {flex : 1});

        this._initForm();

        var hcont = new qx.ui.container.Composite(new qx.ui.layout.HBox(5).set({"alignX" : "right"}));
        hcont.setPadding(5);

        var bt = this._okBt = new qx.ui.form.Button(this.tr("Ok"));
        bt.addListener("execute", this._ok, this);
        hcont.add(bt);

        bt = new qx.ui.form.Button(this.tr("Cancel"));
        bt.addListener("execute", this.close, this);
        hcont.add(bt);
        this.add(hcont);

        var cmd = this.createCommand("Esc");
        cmd.addListener("execute", this.close, this);
        this.addListenerOnce("resize", this.center, this);

        selector.addListener("pageSelected", this._syncState, this);
        this._syncState();
    },

    members : {

        _options : null,

        _selector : null,

        _okBt : null,

        _ok : function() {
            this._selector.getSelectedPageWithExtraInfo(function(sp) {
                if (sp != null || this._options["allowRootSelection"]) {
                    this.fireDataEvent("completed", sp);
                }
            }, this);
        },

        _syncState : function() {
            if (!this._options["allowRootSelection"]) {
                this._okBt.setEnabled(this._selector.getSelectedPage() != null);
            }
        },

        _initForm : function() {

        },

        close : function() {
            this.base(arguments);
            this.destroy();
        }
    },

    destruct : function() {
        this._okBt = null;
        this._selector = null;
    }
});
