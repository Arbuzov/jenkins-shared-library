<table class="relative-table" style="width: 60%;" data-snooker-col-series="numbers" data-snooker-locked-cols="0">
<% if (binding.hasVariable('renderHeader') && renderHeader) { %>
<% if (values?.size()) { %>
  <colgroup>
    <% values?.first()?.each { %>
      <col/>
    <% } %>
  </colgroup>
<% } %>
<% } %>
  <tbody>
<% if (binding.hasVariable('renderHeader') && renderHeader) { %>
<% if (values?.size()) { %>
    <tr>
    <% values?.first()?.each { cell -> %>
      <th scope="col">${
        cell.replaceAll(/(\r\n|\r|\n)/, '\\\\n')
            .replaceAll(/\n/, '')
            .replaceAll(/>\s+</, '><')
            .replaceAll('"', '&quot;')
            .replaceAll("'", '&#39;')
            .replaceAll('>', '&gt;')
            .replaceAll('<', '&lt;')
            .replaceAll('µ', '&micro;')
        }</th>
    <% } %>
    </tr>
<% } %>
<% } %>
  <% values?.each { row -> %>
    <tr>
      <% row.each { cell -> %>
        <td class="snooker-col-series" data-snooker-col-series="numbers">
          <div class="snooker-cell-content">
            <div class="snooker-cell-content-inner">
              ${
                cell.replaceAll(/(\r\n|\r|\n)/, '\\\\n')
                    .replaceAll(/\n/, '')
                    .replaceAll(/>\s+</, '><')
                    .replaceAll('"', '&quot;')
                    .replaceAll("'", '&#39;')
                    .replaceAll('>', '&gt;')
                    .replaceAll('<', '&lt;')
                    .replaceAll('µ', '&micro;')
              }
            </div>
          </div>
        </td>
      <% } %>
    </tr>
  <% } %>
  </tbody>
</table>
