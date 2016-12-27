qx.Class.define("ncms.pgs.PageReferersTable", {
    extend: sm.table.Table,

    construct: function (item) {
        var cmeta = {
            icon: {
                title: "",
                type: "image",
                width: 26,
                sortable: false
            },
            name: {
                title: this.tr("Name").toString()
            },
            path: {
                title: this.tr("Path").toString(),
                sortable: false
            }
        };

        var useColumns = ["icon", "name", "path"];
        var tm = new sm.model.RemoteVirtualTableModel(cmeta).set({
            "useColumns": useColumns,
            "rowdataUrl": ncms.Application.ACT.getRestUrl("pages.referrers", {guid: item.getGuid()}),
            "rowcountUrl": ncms.Application.ACT.getRestUrl("pages.referrers.count", {id: item.getId()})
        });

        var custom = {
            tableColumnModel: function () {
                return new sm.model.JsonTableColumnModel(
                    useColumns.map(function (cname) {
                        return cmeta[cname];
                    }));
            }
        };
        tm.setViewSpec({sortInd: 1});
        this.base(arguments, tm, custom);
    },

    members: {
        setViewSpec: function (spec) {
            this.getTableModel().setViewSpec(spec);
        },

        getViewSpec: function () {
            return this.getTableModel().getViewSpec();
        }
    }
});