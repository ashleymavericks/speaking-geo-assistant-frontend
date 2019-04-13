package `in`.jhalwabois.geogpassist.adapters

import `in`.jhalwabois.geogpassist.R
import android.location.Location
import android.support.v4.content.ContextCompat
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.display_card.view.*

/**
 * Created by betterclever on 31/3/18.
 */

data class CardData(
        val lat: Double = 0.0,
        val long: Double = 0.0,
        val area: Double = 0.0
)

class CardListAdapter : RecyclerView.Adapter<CardListAdapter.ViewHolder>() {

    var locations: List<CardData> = emptyList()
    var selectedItem = 0

    fun updateLocations(list: List<CardData>) {
        this.locations = list
        selectedItem = 0
        notifyDataSetChanged()
    }

    var onItemSelected: (index: Int) -> Unit = {}

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.display_card, parent, false))

    override fun getItemCount() = locations.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val selectItem = {
            selectedItem = position
            onItemSelected(position)
            notifyDataSetChanged()
        }
        holder.bindItem(locations[position], position == selectedItem, selectItem)
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bindItem(data: CardData, isSelected: Boolean, selectItem: () -> Unit) {
            itemView.cardBgLL.background = if (isSelected)
                ContextCompat.getDrawable(itemView.context, R.drawable.card_border)
            else null
            itemView.setOnClickListener { selectItem() }

            itemView.mainTV.text =
                    "${Location.convert(data.lat, Location.FORMAT_DEGREES)} " +
                    "${Location.convert(data.long, Location.FORMAT_DEGREES)}"

            itemView.supportingTV.text = "${data.area} Sq km"
        }
    }
}

