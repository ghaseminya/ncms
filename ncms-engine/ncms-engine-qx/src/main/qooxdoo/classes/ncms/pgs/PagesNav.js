/**
 * Pages selector.
 */
qx.Class.define("ncms.pgs.PagesNav", {
    extend : qx.ui.core.Widget,

    statics : {
    },

    events : {
    },

    properties : {
    },

    construct : function() {
        this.base(arguments);
        this._setLayout(new qx.ui.layout.Grow());
        this.setBackgroundColor("blue");

    },

    members : {

    },

    destruct : function() {
        //this._disposeObjects("__field_name");                                
    }
});