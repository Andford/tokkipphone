// SPDX-License-Identifier: GPL-3.0-or-later
import QtQuick
import mx.loperstack.tokkiphone

// First run: pair a phone. Also covers the moment before the core is ready
// and the (rare) case where it failed to start.
Item {
    id: page

    readonly property string status: AppController.status

    Column {
        anchors.centerIn: parent
        width: Math.min(460, parent.width - 48)
        spacing: 22

        TokkipFigure {
            anchors.horizontalCenter: parent.horizontalCenter
            width: 120; height: 120
            animate: !Theme.reduceMotion
            accent: Theme.graphite ? Theme.surfaceContent : Theme.primary
            cookieColor: Qt.rgba(Theme.primary.r, Theme.primary.g, Theme.primary.b, Theme.dark ? 0.22 : 0.14)
            mood: page.status === "ready" ? "happy" : page.status === "failed" ? "sad" : "working"
        }
        Column {
            width: parent.width
            spacing: 6
            Txt {
                width: parent.width
                horizontalAlignment: Text.AlignHCenter
                text: qsTr("Welcome to Tokkip Phone")
                role: "displaySmall"
            }
            Txt {
                width: parent.width
                horizontalAlignment: Text.AlignHCenter
                wrapMode: Text.WordWrap
                role: "body"
                muted: true
                text: page.status === "failed"
                      ? qsTr("Tokkip Phone couldn't start.")
                      : qsTr("Your phone and this PC, together. Pair once and they find each other from then on.")
            }
        }

        Card {
            width: parent.width
            padding: 24
            visible: page.status === "ready"
            PairingPanel {
                width: parent.width
                cancellable: false
                running: page.visible && page.status === "ready"
            }
        }

        Spinner {
            anchors.horizontalCenter: parent.horizontalCenter
            visible: page.status === "starting"
            width: 28; height: 28
        }

        Column {
            width: parent.width
            spacing: 12
            visible: page.status === "failed"
            Txt {
                width: parent.width
                horizontalAlignment: Text.AlignHCenter
                wrapMode: Text.WordWrap
                role: "code"
                size: 12
                muted: true
                text: AppController.error
            }
            Button {
                anchors.horizontalCenter: parent.horizontalCenter
                variant: "tonal"
                iconPath: Icons.folder
                text: qsTr("Open the logs folder")
                onClicked: Qt.openUrlExternally(AppController.logsUrl())
            }
        }
    }
}
