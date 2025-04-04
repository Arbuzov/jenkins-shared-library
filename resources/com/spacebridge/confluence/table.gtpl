<table class="relative-table" style="width: 60%;" data-snooker-col-series="numbers" data-snooker-locked-cols="0">
  <tbody>
  <% values?.each { row -> %>
    <tr>
      <% row.each { cell -> %>
        <td class="snooker-col-series" data-snooker-col-series="numbers">
          <div class="snooker-cell-content">
            <div class="snooker-cell-content-inner">
              ${cell}
            </div>
          </div>
        </td>
      <% } %>
    </tr>
  <% } %>
  </tbody>
</table>