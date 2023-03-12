package com.example.weatherapp.fragments

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.activityViewModels
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.example.weatherapp.DialogManager
import com.example.weatherapp.MainViewModel
import com.example.weatherapp.R
import com.example.weatherapp.adapters.VpAdapter
import com.example.weatherapp.adapters.WeatherModel
import com.example.weatherapp.databinding.FragmentMainBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.material.tabs.TabLayoutMediator
import com.squareup.picasso.Picasso
import org.json.JSONObject

const val API_KEY = "7418b33c23934a0eb4d101508231003"

class MainFragment : Fragment() {
    private lateinit var fLocationClient: FusedLocationProviderClient
    private lateinit var pLauncher: ActivityResultLauncher<String>
    private lateinit var binding: FragmentMainBinding
    private val model: MainViewModel by activityViewModels()

    private val listFr = listOf(
        HoursFragment.newInstance(),
        DaysFragment.newInstance()
    )
    private val listFrName = listOf(
        "HOURS",
        "DAYS"
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentMainBinding.inflate(inflater, container, false)
        //binding.imBackGrnd.setImageResource(R.drawable.fon2)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        checkPermission()
        init()
        updateCurrentCard()
        //requestWeatherDate("Sochi", "7")
        //getLocation()

    }

    override fun onResume() {
        super.onResume()
        checkLocation()
    }


    private fun init() {
        fLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())
        val adapter = VpAdapter(activity as FragmentActivity, listFr)
        binding.vp2.adapter = adapter
        TabLayoutMediator(binding.tabLayout, binding.vp2) { tab, pos ->
            tab.text = listFrName[pos]
        }.attach()
        binding.ibSync.setOnClickListener {
            binding.tabLayout.selectTab(binding.tabLayout.getTabAt(0))
            checkLocation()
        }
        binding.ibSearch.setOnClickListener {
            DialogManager.searchByNameDialog(requireContext(), object : DialogManager.Listener{
                override fun onClick(name: String?) {
                    Log.d("MyLog", "City name => $name")
                    if (name != null) {
                        requestWeatherDate(name)
                    }
                }
            })
        }

    }

    private fun isLocationEnable() : Boolean{
        val lm = activity?.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }

    private fun checkLocation(){
        if (isLocationEnable()) {
            getLocation()
        } else {
            DialogManager.locatiobSettigsDialog(requireContext(), object : DialogManager.Listener{
                override fun onClick(name: String?) {
                    startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }
            })
        }
    }


    private fun getLocation() {
        val ct = CancellationTokenSource()
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            return
        }
        fLocationClient
            .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, ct.token)
            .addOnCompleteListener {
                requestWeatherDate("${it.result.latitude},${it.result.longitude}") }
    }

    private fun updateCurrentCard(){
        model.liveDataCurrent.observe(viewLifecycleOwner){
            item ->
            binding.tvData.text = item.time
            binding.tvCity.text = item.city
            var temp = ""
            if (item.currentTemp != ""){
                temp = item.currentTemp + "°C"
            } else {
                temp = "${item.minTemp}°C / ${item.maxTemp}°C"
            }
            binding.tvCurrentTemp.text = temp
            binding.tvCondition.text = item.condition
            Picasso.get().load("https:"+item.imageUrl).into(binding.imWeather)
            val text = item.minTemp + "°C / " + item.maxTemp + "°C"
            binding.tvMaxMinTemp.text = if(item.currentTemp.isEmpty()) "" else text
        }
    }

    private fun permissionListener() {
        pLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) {
            Toast.makeText(activity, "Permission is $it", Toast.LENGTH_LONG).show()
        }
    }

    private fun checkPermission() {
        if (!isPermissionGranted(Manifest.permission.ACCESS_FINE_LOCATION)) {
            permissionListener()
            pLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun requestWeatherDate(city: String) {
        val days = "7"
        val lang = "en"
        val url =
            "https://api.weatherapi.com/v1/forecast.json?key=" +
                    API_KEY +
                    "&q=" +
                    city +
                    "&days=" +
                    days +
                    "&aqi=no&alerts=no" +
                    "&lang=" +
                    lang
        val queue = Volley.newRequestQueue(context)
        val request = StringRequest(
            Request.Method.GET,
            url,
            { result -> parseWeatherData(result)
                //Log.d("MyLog", "Result Request => $result")
            },
            { error ->
                Log.d("MyLog", "Error Request => $error")
            }
        )
        queue.add(request)

    }

    private fun parseWeatherData(result: String) {
        val mainObject = JSONObject(result)
        val list = parseDays(mainObject)
        parseCurrentData(mainObject, list[0])

    }
    private fun parseDays(mainObject: JSONObject): List<WeatherModel> {
        val list = ArrayList<WeatherModel>()
        val daysArray = mainObject.getJSONObject("forecast").getJSONArray("forecastday")
        val city = mainObject.getJSONObject("location").getString("name")
        for (i in 0 until daysArray.length()) {
            val day = daysArray[i] as JSONObject
            val item = WeatherModel(
                city,
                day.getString("date"),
                day.getJSONObject("day").getJSONObject("condition").getString("text"),
                "",
                day.getJSONObject("day").getJSONObject("condition").getString("icon"),
                day.getJSONObject("day").getString("maxtemp_c").toFloat().toInt().toString(),
                day.getJSONObject("day").getString("mintemp_c").toFloat().toInt().toString(),
                day.getJSONArray("hour").toString()
            )
            list.add(item)
        }
        model.liveDataList.value = list
        return list
    }

    private fun parseCurrentData(mainObject: JSONObject, weatherDay: WeatherModel) {
        val item = WeatherModel(
            mainObject.getJSONObject("location").getString("name"),
            mainObject.getJSONObject("current").getString("last_updated"),
            mainObject.getJSONObject("current").getJSONObject("condition").getString("text"),
            mainObject.getJSONObject("current").getString("temp_c").toFloat().toInt().toString(),
            mainObject.getJSONObject("current").getJSONObject("condition").getString("icon"),
            weatherDay.maxTemp,
            weatherDay.minTemp,
            weatherDay.hours
        )
        Log.d("MyLog", "Result Request Hours Main => ${item.hours}")


        model.liveDataCurrent.value = item

    }

    companion object {
        @JvmStatic
        fun newInstance() = MainFragment()
    }
}